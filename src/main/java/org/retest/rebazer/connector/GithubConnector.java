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

public class GithubConnector implements RepositoryConnector {

	private static final String BASE_URL = "https://api.github.com";

	private final RestTemplate template;

	public GithubConnector( final RepositoryConfig repoConfig, final RestTemplateBuilder builder ) {
		final String basePath = "/repos/" + repoConfig.getTeam() + "/" + repoConfig.getRepo();

		template = builder.basicAuthorization( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( BASE_URL + basePath ).build();
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		return PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.lastUpdate( jsonPath.read( "$.updated_at" ) ) //
				.build();
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/reviews" );
		return jsonPath.<List<String>> read( "$..state" ).stream().anyMatch( s -> "APPROVED".equals( s ) );
	}

	@Override
	public boolean rebaseNeeded( final PullRequest pullRequest ) {
		return !getLastCommonCommitId( pullRequest ).equals( getHeadOfBranch( pullRequest ) );
	}

	String getHeadOfBranch( final PullRequest pullRequest ) {
		return jsonPathForPath( "/git/refs/heads/" + pullRequest.getDestination() ).read( "$.object.sha" );
	}

	String getLastCommonCommitId( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/commits" );

		final List<String> commitIds = jsonPath.read( "$..sha" );
		final List<String> parentIds = jsonPath.read( "$..parents..sha" );

		return parentIds.stream().filter( parent -> commitIds.contains( parent ) ).findFirst()
				.orElseThrow( IllegalStateException::new );
	}

	@Override
	public void merge( final PullRequest pullRequest ) {
		final Map<String, String> request = new HashMap<>();
		request.put( "commit_title", pullRequest.mergeCommitMessage() );
		request.put( "merge_method", "merge" );

		template.put( requestPath( pullRequest ) + "/merge", request, Object.class );

		template.delete( "/git/refs/heads/" + pullRequest.getSource() );
	}

	@Override
	public boolean greenBuildExists( final PullRequest pullRequest ) {
		final String urlPath = "/commits/" + pullRequest.getSource() + "/status";
		final DocumentContext jsonPath = jsonPathForPath( urlPath );
		return jsonPath.<List<String>> read( "$.statuses[*].state" ).stream().anyMatch( s -> "success".equals( s ) );
	}

	@Override
	public List<PullRequest> getAllPullRequests() {
		final DocumentContext jsonPath = jsonPathForPath( "/pulls" );
		return parsePullRequestsJson( jsonPath );
	}

	public static List<PullRequest> parsePullRequestsJson( final DocumentContext jsonPath ) {
		final int size = jsonPath.read( "$.length()" );
		final List<PullRequest> results = new ArrayList<>( size );
		for ( int i = 0; i < size; i++ ) {
			final int id = jsonPath.read( "$.[" + i + "].number" );
			final String source = jsonPath.read( "$.[" + i + "].head.ref" );
			final String destination = jsonPath.read( "$.[" + i + "].base.ref" );
			final String lastUpdate = jsonPath.read( "$.[" + i + "].updated_at" );
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
		return "/pulls/" + pullRequest.getId();
	}

	private DocumentContext jsonPathForPath( final String urlPath ) {
		final String json = template.getForObject( urlPath, String.class );
		return JsonPath.parse( json );
	}

	@Override
	public void addComment( final PullRequest pullRequest, final String message ) {
		final Map<String, String> request = new HashMap<>();
		request.put( "body", message );
		template.postForObject( "/issues/" + pullRequest.getId() + "/comments", request, String.class );
	}

}
