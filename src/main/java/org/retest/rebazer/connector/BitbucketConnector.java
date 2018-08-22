package org.retest.rebazer.connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class BitbucketConnector implements RepositoryConnector {

	private static final String baseUrlV1 = "https://api.bitbucket.org/1.0";
	private static final String baseUrlV2 = "https://api.bitbucket.org/2.0";

	private final RestTemplate legacyTemplate;
	private final RestTemplate template;

	public BitbucketConnector( final RepositoryConfig repoConfig, final RestTemplateBuilder templateBuilder ) {
		final String basePath = "/repositories/" + repoConfig.getTeam() + "/" + repoConfig.getRepo();

		legacyTemplate = templateBuilder.basicAuthorization( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( baseUrlV1 + basePath ).build();
		template = templateBuilder.basicAuthorization( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( baseUrlV2 + basePath ).build();
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final PullRequest updatedPullRequest = PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.lastUpdate( jsonPath.read( "$.updated_on" ) ) //
				.build();
		return updatedPullRequest;
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		return jsonPath.<List<Boolean>> read( "$.participants[*].approved" ).stream().anyMatch( approved -> approved );
	}

	@Override
	public boolean rebaseNeeded( final PullRequest pullRequest ) {
		return !getLastCommonCommitId( pullRequest ).equals( getHeadOfBranch( pullRequest ) );
	}

	String getHeadOfBranch( final PullRequest pullRequest ) {
		return jsonPathForPath( "/refs/branches/" + pullRequest.getDestination() ).read( "$.target.hash" );
	}

	String getLastCommonCommitId( final PullRequest pullRequest ) {
		DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/commits" );

		final int pageLength = jsonPath.read( "$.pagelen" );
		final int size = jsonPath.read( "$.size" );
		final int lastPage = (pageLength + size - 1) / pageLength;

		if ( lastPage > 1 ) {
			jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/commits?page=" + lastPage );
		}

		final List<String> commitIds = jsonPath.read( "$.values[*].hash" );
		final List<String> parentIds = jsonPath.read( "$.values[*].parents[0].hash" );

		return parentIds.stream().filter( parent -> !commitIds.contains( parent ) ).findFirst()
				.orElseThrow( IllegalStateException::new );
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
		return jsonPath.<List<String>> read( "$.values[*].state" ).stream().anyMatch( s -> "SUCCESSFUL".equals( s ) );
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
			final int id = jsonPath.read( "$.values[" + i + "].id" );
			final String source = jsonPath.read( "$.values[" + i + "].source.branch.name" );
			final String destination = jsonPath.read( "$.values[" + i + "].destination.branch.name" );
			final String lastUpdate = jsonPath.read( "$.values[" + i + "].updated_on" );
			results.add( PullRequest.builder() //
					.id( id ) //
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
		final Map<String, String> request = new HashMap<>();
		request.put( "content", message );

		legacyTemplate.postForObject( requestPath( pullRequest ) + "/comments", request, String.class );
	}

}
