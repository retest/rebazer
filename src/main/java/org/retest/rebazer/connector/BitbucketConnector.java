package org.retest.rebazer.connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryTeam;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;


public class BitbucketConnector implements RepositoryConnector {

	private final static String baseUrlV1 = "https://api.bitbucket.org/1.0";
	private final static String baseUrlV2 = "https://api.bitbucket.org/2.0";

	private final RestTemplate legacyTemplate;
	private final RestTemplate template;

	public BitbucketConnector( final RepositoryTeam team, final RepositoryConfig repo, final RestTemplateBuilder builder ) {
		final String basePath = "/repositories/" + team.getName() + "/" + repo.getName();

		legacyTemplate =
				builder.basicAuthorization( team.getUser(), team.getPass() ).rootUri( baseUrlV1 + basePath ).build();
		template = builder.basicAuthorization( team.getUser(), team.getPass() ).rootUri( baseUrlV2 + basePath ).build();
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jp = jsonPathForPath( pullRequest.getUrl() );
		final PullRequest updatedPullRequest = PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.repo( pullRequest.getRepo() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.url( pullRequest.getUrl() ) //
				.lastUpdate( jp.read( "$.updated_on" ) ) //
				.build();
		return updatedPullRequest;
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jp = jsonPathForPath( pullRequest.getUrl() );
		return jp.<List<Boolean>> read( "$.participants[*].approved" ).stream().anyMatch( approved -> approved );
	}

	@Override
	public boolean rebaseNeeded( final PullRequest pullRequest ) {
		return !getLastCommonCommitId( pullRequest ).equals( getHeadOfBranch( pullRequest ) );
	}

	String getHeadOfBranch( final PullRequest pullRequest ) {
		return jsonPathForPath( "/refs/branches/" + pullRequest.getDestination() ).read( "$.target.hash" );
	}

	String getLastCommonCommitId( final PullRequest pullRequest ) {
		DocumentContext jp = jsonPathForPath( pullRequest.getUrl() + "/commits" );

		final int pageLength = jp.read( "$.pagelen" );
		final int size = jp.read( "$.size" );
		final int lastPage = (pageLength + size - 1) / pageLength;

		if ( lastPage > 1 ) {
			jp = jsonPathForPath( pullRequest.getUrl() + "/commits?page=" + lastPage );
		}

		final List<String> commitIds = jp.read( "$.values[*].hash" );
		final List<String> parentIds = jp.read( "$.values[*].parents[0].hash" );

		return parentIds.stream().filter( parent -> !commitIds.contains( parent ) ).findFirst()
				.orElseThrow( IllegalStateException::new );
	}

	@Override
	public void merge( final PullRequest pullRequest ) {
		final String message = String.format( "Merged in %s (pull request #%d) by ReBaZer", pullRequest.getSource(),
				pullRequest.getId() );
		// TODO add approver to message?
		final Map<String, Object> request = new HashMap<>();
		request.put( "close_source_branch", true );
		request.put( "message", message );
		request.put( "merge_strategy", "merge_commit" );

		template.postForObject( pullRequest.getUrl() + "/merge", request, Object.class );
	}

	@Override
	public boolean greenBuildExists( final PullRequest pullRequest ) {
		final DocumentContext jp = jsonPathForPath( pullRequest.getUrl() + "/statuses" );
		return jp.<List<String>> read( "$.values[*].state" ).stream().anyMatch( s -> s.equals( "SUCCESSFUL" ) );
	}

	@Override
	public List<PullRequest> getAllPullRequests( final RepositoryConfig repo ) {
		final DocumentContext jp = jsonPathForPath( "/pullrequests" );
		return parsePullRequestsJson( repo, jp );
	}

	public List<PullRequest> parsePullRequestsJson( final RepositoryConfig repo, final DocumentContext jp ) {
		final int numPullRequests = (int) jp.read( "$.size" );
		final List<PullRequest> results = new ArrayList<>( numPullRequests );
		for ( int i = 0; i < numPullRequests; i++ ) {
			final int id = jp.read( "$.values[" + i + "].id" );
			final String source = jp.read( "$.values[" + i + "].source.branch.name" );
			final String destination = jp.read( "$.values[" + i + "].destination.branch.name" );
			final String lastUpdate = jp.read( "$.values[" + i + "].updated_on" );
			results.add( PullRequest.builder() //
					.id( id ) //
					.repo( repo.getName() ) //
					.source( source ) //
					.destination( destination ) //
					.url( "/pullrequests/" + id ) //
					.lastUpdate( lastUpdate ) //
					.build() ); //
		}
		return results;
	}

	private DocumentContext jsonPathForPath( final String urlPath ) {
		final String json = template.getForObject( urlPath, String.class );
		return JsonPath.parse( json );
	}

	@Override
	public void addComment( final PullRequest pullRequest ) {
		final Map<String, String> request = new HashMap<>();
		request.put( "content", "This pull request needs some manual love ..." );

		legacyTemplate.postForObject( pullRequest.getUrl() + "/comments", request, String.class );
	}

}
