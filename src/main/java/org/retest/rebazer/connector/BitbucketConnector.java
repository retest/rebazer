package org.retest.rebazer.connector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.domain.BitbucketPullRequestResponse;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.retest.rebazer.service.PullRequestLastUpdateStore;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BitbucketConnector implements RepositoryConnector {

	private final RestTemplate template;
	private final ObjectMapper objectMapper;

	public BitbucketConnector( final RepositoryConfig repoConfig, final RestTemplateBuilder templateBuilder ) {
		final String basePath = "/2.0/repositories/" + repoConfig.getTeam() + "/" + repoConfig.getRepo();

		template = templateBuilder.basicAuthentication( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( repoConfig.getApiHost() + basePath ).build();

		objectMapper = new ObjectMapper().configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false );
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final Date repositoryTime = PullRequestLastUpdateStore.parseStringToDate( jsonPath.read( "$.updated_on" ) );
		return PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.title( pullRequest.getTitle() ) //
				.description( pullRequest.getDescription() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.lastUpdate( repositoryTime ) //
				.build();
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final List<Boolean> reviewers = jsonPath.<List<Boolean>> read( "$.participants[*].approved" );

		return pullRequest.isReviewByAllReviewersRequested() && !reviewers.isEmpty()
				? reviewers.stream().allMatch( approved -> approved )
				: reviewers.stream().anyMatch( approved -> approved );
	}

	@Override
	public boolean rebaseNeeded( final PullRequest pullRequest ) {
		return !getLastParentCommitId( pullRequest ).equals( getHeadOfBranch( pullRequest ) );
	}

	String getHeadOfBranch( final PullRequest pullRequest ) {
		return jsonPathForPath( "/refs/branches/" + pullRequest.getDestination() ).read( "$.target.hash" );
	}

	String getLastParentCommitId( final PullRequest pullRequest ) {
		final DocumentContext document = getLastPage( pullRequest );
		final List<String> parentIds = document.read( "$.values[*].parents[0].hash" );
		return parentIds.get( parentIds.size() - 1 );
	}

	@Override
	public void merge( final PullRequest pullRequest ) {
		final Map<String, Object> request = new HashMap<>();
		request.put( "close_source_branch", true );
		request.put( "message", pullRequest.mergeCommitMessage() );
		request.put( "merge_strategy", "merge_commit" );

		template.postForObject( requestPath( pullRequest ) + "/merge", request, Object.class );
	}

	@Override
	public boolean greenBuildExists( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/statuses" );
		return jsonPath.<List<String>> read( "$.values[*].state" ).stream().anyMatch( "SUCCESSFUL"::equals );
	}

	@Override
	public List<PullRequest> getAllPullRequests() {
		final DocumentContext jsonPath = jsonPathForPath( "/pullrequests" );
		return parsePullRequestsJson( jsonPath );
	}

	public static List<PullRequest> parsePullRequestsJson( final DocumentContext jsonPath ) {
		final int numPullRequests = jsonPath.read( "$.size" );
		final List<PullRequest> results = new ArrayList<>( numPullRequests );
		for ( int i = 0; i < numPullRequests; i++ ) {
			final String pathPrefix = "$.values[" + i + "].";
			final int id = jsonPath.read( pathPrefix + "id" );
			final String title = jsonPath.read( pathPrefix + "title" );
			final String description = jsonPath.read( pathPrefix + "description" );
			final String source = jsonPath.read( pathPrefix + "source.branch.name" );
			final String destination = jsonPath.read( pathPrefix + "destination.branch.name" );
			final Date lastUpdate =
					PullRequestLastUpdateStore.parseStringToDate( jsonPath.read( pathPrefix + "updated_on" ) );
			results.add( PullRequest.builder() //
					.id( id ) //
					.title( title ) //
					.description( description ) //
					.source( source ) //
					.destination( destination ) //
					.lastUpdate( lastUpdate ) //
					.build() ); //
		}
		return results;
	}

	private static String requestPath( final PullRequest pullRequest ) {
		return "/pullrequests/" + pullRequest.getId();
	}

	private DocumentContext jsonPathForPath( final String urlPath ) {
		final String json = template.getForObject( urlPath, String.class );
		return JsonPath.parse( json );
	}

	@Override
	public void addComment( final PullRequest pullRequest, final String message ) {
		final ObjectNode messageNode = objectMapper.createObjectNode();
		messageNode.put( "raw", message );
		final ObjectNode contentNode = objectMapper.createObjectNode();
		contentNode.set( "content", messageNode );

		template.postForObject( requestPath( pullRequest ) + "/comments", contentNode, String.class );
	}

	private DocumentContext getLastPage( final PullRequest pullRequest ) {
		DocumentContext document = jsonPathForPath( requestPath( pullRequest ) + "/commits" );

		try {
			BitbucketPullRequestResponse response =
					objectMapper.readValue( document.jsonString(), BitbucketPullRequestResponse.class );

			while ( response.getNext() != null ) {
				final String url = response.getNext();
				document = jsonPathForPath( url );
				response = objectMapper.readValue( document.jsonString(), BitbucketPullRequestResponse.class );
			}
		} catch ( final IOException e ) {
			log.error( "Error parsing JSON.", e );
		}

		return document;
	}
}
