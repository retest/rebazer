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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GithubConnector implements RepositoryConnector {

	private final static String baseUrl = "https://api.github.com/";

	private final RestTemplate template;

	public GithubConnector( final RepositoryTeam repoTeam, final RepositoryConfig repoConfig,
			final RestTemplateBuilder builder ) {
		final String basePath = "/repos/" + repoTeam.getName() + "/" + repoConfig.getName();

		template = builder.basicAuthorization( repoTeam.getUser(), repoTeam.getPass() ).rootUri( baseUrl + basePath )
				.build();
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final PullRequest updatedPullRequest = PullRequest.builder() //
				.id( pullRequest.getId() ) //
				.source( pullRequest.getSource() ) //
				.destination( pullRequest.getDestination() ) //
				.lastUpdate( jsonPath.read( "$.updated_at" ) ) //
				.build();
		return updatedPullRequest;
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/reviews" );
		final List<String> states = jsonPath.read( "$..state" );
		boolean approved = false;
		for ( final String state : states ) {
			if ( state.equals( "APPROVED" ) ) {
				approved = true;
			} else {
				approved = false;
			}
		}
		return approved;
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
		log.warn( "Merging pull request {}", pullRequest );
		final String message = String.format( "Merged in %s (pull request #%d) by ReBaZer", pullRequest.getSource(),
				pullRequest.getId() );
		final Map<String, String> request = new HashMap<>();
		request.put( "commit_title", message );
		request.put( "merge_method", "merge" );

		template.put( requestPath( pullRequest ) + "/merge", request, Object.class );
	}

	@Override
	public boolean greenBuildExists( final PullRequest pullRequest ) {
		final String urlPath = "/commits/" + pullRequest.getSource() + "/status";
		final DocumentContext jsonPath = jsonPathForPath( urlPath );
		return jsonPath.<List<String>> read( "$.statuses[*].state" ).stream().anyMatch( s -> s.equals( "success" ) );
	}

	@Override
	public List<PullRequest> getAllPullRequests() {
		final DocumentContext jsonPath = jsonPathForPath( "/pulls" );
		return parsePullRequestsJson( jsonPath );
	}

	public static List<PullRequest> parsePullRequestsJson( final DocumentContext jsonPath ) {
		final List<Integer> pullRequestAmount = jsonPath.read( "$..number" );
		final int numPullRequests = pullRequestAmount.size();
		final List<PullRequest> results = new ArrayList<>( numPullRequests );
		for ( int i = 0; i < numPullRequests; i++ ) {
			final int id = pullRequestAmount.get( i );
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
	public void addComment( final PullRequest pullRequest ) {
		final Map<String, String> request = new HashMap<>();
		request.put( "body", "This pull request needs some manual love ..." );
		template.postForObject( "/issues/" + pullRequest.getId() + "/comments", request, String.class );
	}

}
