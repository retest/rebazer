package org.retest.rebazer.connector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class GithubConnector implements RepositoryConnector {

	private static final String GITHUB_PREVIEW_JSON_MEDIATYPE = "application/vnd.github.antiope-preview+json";

	private static final String BASE_URL = "https://api.github.com";

	private final RestTemplate template;

	public GithubConnector( final RepositoryConfig repoConfig, final RestTemplateBuilder builder ) {
		final String basePath = "/repos/" + repoConfig.getTeam() + "/" + repoConfig.getRepo();

		template = builder.basicAuthentication( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( BASE_URL + basePath ).build();

	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final String repositoryTime = jsonPath.read( "$.updated_at" );
		final String checksTime = newestChecksTime( pullRequest );
		final int solution = repositoryTime.compareTo( checksTime );
		final PullRequest result = PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.lastUpdate( solution > 0 ? repositoryTime : checksTime ) //
				.build();

		return result;
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/reviews" );
		return jsonPath.<List<String>> read( "$..state" ).stream().anyMatch( "APPROVED"::equals );
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

		return parentIds.stream().filter( commitIds::contains ).findFirst().orElseThrow( IllegalStateException::new );
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
		return getGitHubChecks( pullRequest ).stream().allMatch( "success"::equals );
	}

	public String newestChecksTime( final PullRequest pullRequest ) {
		return getGitHubChecksTimeStamps( pullRequest ).stream()//
				.filter( time -> time != null && !time.isEmpty() )//
				.sorted( Comparator.reverseOrder() )//
				.findFirst()//
				.orElse( pullRequest.getLastUpdate() );
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

	private List<String> getGitHubChecks( final PullRequest pullRequest ) {
		final String pr = template.getForObject( "/pulls/" + pullRequest.getId(), String.class );
		final String head = JsonPath.parse( pr ).read( "$.head.sha" );
		final String checksUrl = "/commits/" + head + "/check-runs";

		final HttpHeaders headers = new HttpHeaders();
		headers.setAccept( Collections.singletonList( MediaType.parseMediaType( GITHUB_PREVIEW_JSON_MEDIATYPE ) ) );
		final HttpEntity<String> entity = new HttpEntity<>( "parameters", headers );

		final ResponseEntity<String> json = template.exchange( checksUrl, HttpMethod.GET, entity, String.class );

		return JsonPath.parse( json.getBody() ).<List<String>> read( "$.check_runs[*].conclusion" );
	}

	private List<String> getGitHubChecksTimeStamps( final PullRequest pullRequest ) {
		final String urlPath = "/commits/" + pullRequest.getSource() + "/check-runs";

		final HttpHeaders headers = new HttpHeaders();
		headers.add( "Accept", "application/vnd.github.antiope-preview+json" );

		final HttpEntity<String> entity = new HttpEntity<>( "parameters", headers );

		final ResponseEntity<String> json = template.exchange( urlPath, HttpMethod.GET, entity, String.class );

		return JsonPath.parse( json.getBody() ).<List<String>> read( "$.check_runs[*].completed_at" );
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
