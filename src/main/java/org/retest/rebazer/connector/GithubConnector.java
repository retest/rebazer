package org.retest.rebazer.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.retest.rebazer.service.PullRequestLastUpdateStore;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GithubConnector implements RepositoryConnector {

	private static final String GITHUB_PREVIEW_JSON_MEDIATYPE = "application/vnd.github.antiope-preview+json";

	private final RestTemplate template;

	public GithubConnector( final RepositoryConfig repoConfig, final RestTemplateBuilder builder ) {
		final String basePath = "/repos/" + repoConfig.getTeam() + "/" + repoConfig.getRepo();

		template = builder.basicAuthentication( repoConfig.getUser(), repoConfig.getPass() )
				.rootUri( repoConfig.getApiHost() + basePath ).build();
	}

	@Override
	public PullRequest getLatestUpdate( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) );
		final String repositoryTimeAsString = jsonPath.read( "$.updated_at" );
		final Date repositoryTime = PullRequestLastUpdateStore.parseStringToDate( repositoryTimeAsString );
		final Date checksTime = PullRequestLastUpdateStore.parseStringToDate( newestChecksTime( pullRequest ) );
		return pullRequest.updateLastChange( repositoryTime.after( checksTime ) ? repositoryTime : checksTime );
	}

	@Override
	public boolean isApproved( final PullRequest pullRequest ) {
		safeReviewStates( pullRequest );
		final Collection<String> reviewers = pullRequest.getReviewers().values();

		return pullRequest.isReviewByAllReviewersRequested() && !reviewers.isEmpty()
				? reviewers.stream().allMatch( "APPROVED"::equals )
				: !reviewers.contains( "CHANGES_REQUESTED" ) && reviewers.contains( "APPROVED" );
	}

	private void safeReviewStates( final PullRequest pullRequest ) {
		final DocumentContext jsonPath = jsonPathForPath( requestPath( pullRequest ) + "/reviews" );
		final List<String> reviews = jsonPath.<List<String>> read( "$..state" );
		final Integer creator = pullRequest.getCreator();
		for ( int i = 0; i < reviews.size(); i++ ) {
			final String reviewsState = jsonPath.read( "$.[" + i + "].state" );
			final int reviewer = jsonPath.read( "$.[" + i + "].user.id" );
			if ( reviewer != creator ) {
				if ( !reviewsState.equals( "COMMENTED" ) ) {
					pullRequest.getReviewers().put( reviewer, reviewsState );
				} else {
					pullRequest.getReviewers().compute( reviewer, ( k, v ) -> v == null ? reviewsState : v );
				}
			}
		}
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
		return getGitHubChecks( pullRequest, "conclusion" ).stream().allMatch( "success"::equals );
	}

	String newestChecksTime( final PullRequest pullRequest ) {
		return getGitHubChecks( pullRequest, "completed_at" ).stream()//
				.filter( time -> time != null && !time.isEmpty() )//
				.max( Comparator.naturalOrder() )//
				.orElse( pullRequest.getLastUpdate().toInstant().toString() );
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
			final String fullName = jsonPath.read( "$.[" + i + "].head.repo.full_name" );

			if ( fullName.startsWith( "retest" ) ) {
				final int id = jsonPath.read( "$.[" + i + "].number" );
				final String title = jsonPath.read( "$.[" + i + "].title" );
				final int creator = jsonPath.read( "$.[" + i + "].user.id" );
				final String description = jsonPath.read( "$.[" + i + "].body" );
				final List<Integer> reviewerId = jsonPath.read( "$.[" + i + "].requested_reviewers[*].id" );
				final Map<Integer, String> reviewers = new HashMap<>();
				reviewerId.stream().forEach( userId -> reviewers.put( userId, null ) );
				final String source = jsonPath.read( "$.[" + i + "].head.ref" );
				final String destination = jsonPath.read( "$.[" + i + "].base.ref" );
				final Date lastUpdate =
						PullRequestLastUpdateStore.parseStringToDate( jsonPath.read( "$.[" + i + "].updated_at" ) );
				results.add( PullRequest.builder() //
						.id( id ) //
						.title( title ) //
						.creator( creator ) //
						.description( description ) //
						.reviewers( reviewers ) //
						.source( source ) //
						.destination( destination ) //
						.lastUpdate( lastUpdate ) //
						.build() ); //
			} else {
				log.info( "Ignoring external PR {}", fullName );
			}
		}
		return results;
	}

	private static String requestPath( final PullRequest pullRequest ) {
		return "/pulls/" + pullRequest.getId();
	}

	private List<String> getGitHubChecks( final PullRequest pullRequest, final String instruction ) {
		final String pr = template.getForObject( "/pulls/" + pullRequest.getId(), String.class );
		final String head = JsonPath.parse( pr ).read( "$.head.sha" );
		final String checksUrl = "/commits/" + head + "/check-runs";

		final HttpHeaders headers = new HttpHeaders();
		headers.setAccept( Collections.singletonList( MediaType.parseMediaType( GITHUB_PREVIEW_JSON_MEDIATYPE ) ) );
		final HttpEntity<String> entity = new HttpEntity<>( "parameters", headers );

		final ResponseEntity<String> json = template.exchange( checksUrl, HttpMethod.GET, entity, String.class );

		return JsonPath.parse( json.getBody() ).<List<String>> read( "$.check_runs[*]." + instruction );
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
