package org.retest.rebazer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor( onConstructor = @__( @Autowired ) )
public class GithubService {

	private final RestTemplate githubTemplate;

	private final RebazerConfig config;
	private final RebaseService rebaseService;

	@Scheduled( fixedDelay = 60 * 1000 )
	public void pollGithub() {
		for ( final Repository repo : config.getRepos() ) {
			log.info( "Processing {}.", repo );
			for ( final PullRequest pr : getAllPullRequests( repo ) ) {
				handlePR( repo, pr );
			}
		}
	}

	private void handlePR( final Repository repo, final PullRequest pullRequest ) {
		log.debug( "Processing {}.", pullRequest );

		if ( !greenBuildExists( pullRequest ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
		} else if ( rebaseNeeded( pullRequest ) ) {
			rebase( repo, pullRequest );
		} else if ( !isApproved( pullRequest ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
		} else {
			merge( pullRequest );
		}
	}

	boolean isApproved( final PullRequest pullRequest ) {
		final DocumentContext jp = jsonPathForPath( pullRequest.getUrl() + "/reviews" );
		final List<String> states = jp.read( "$..state" );
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

	boolean rebaseNeeded( final PullRequest pullRequest ) {
		return !getLastCommonCommitId( pullRequest ).equals( getHeadOfBranch( pullRequest ) );
	}

	String getHeadOfBranch( final PullRequest pullRequest ) {
		final String url = "/repos/" + config.getTeam() + "/" + pullRequest.getRepo() + "/";
		return jsonPathForPath( url + "git/refs/heads/" + pullRequest.getDestination() ).read( "$.object.sha" );
	}

	String getLastCommonCommitId( final PullRequest pullRequest ) {
		final DocumentContext jp = jsonPathForPath( pullRequest.getUrl() + "/commits" );

		final List<String> commitIds = jp.read( "$..sha" );
		final List<String> parentIds = jp.read( "$..parents..sha" );

		//		return parentIds.stream().filter( parent -> !commitIds.contains( parent ) ).findFirst()
		//				.orElseThrow( IllegalStateException::new );
		for ( final String parent : parentIds ) {
			if ( commitIds.contains( parent ) ) {
				return parent;
			} else {
				throw new IllegalStateException();
			}
		}
		return null;
	}

	private void merge( final PullRequest pullRequest ) {
		log.warn( "Merging pull request {}", pullRequest );
		final String message = String.format( "Merged in %s (pull request #%d) by ReBaZer", pullRequest.getSource(),
				pullRequest.getId() );
		final Map<String, String> request = new HashMap<>();
		request.put( "commit_title", message );
		request.put( "merge_method", "merge" );

		githubTemplate.put( pullRequest.getUrl() + "/merge", request, Object.class );
	}

	boolean greenBuildExists( final PullRequest pullRequest ) {
		final String urlPath = "/repos/" + config.getTeam() + "/" + pullRequest.getRepo() + "/commits/"
				+ pullRequest.getSource() + "/status";
		final DocumentContext jp = jsonPathForPath( urlPath );
		return jp.<List<String>> read( "$.statuses[*].state" ).stream().anyMatch( s -> s.equals( "success" ) );
	}

	List<PullRequest> getAllPullRequests( final Repository repo ) {
		final String urlPath = "/repos/" + config.getTeam() + "/" + repo.getName() + "/pulls";
		final DocumentContext jp = jsonPathForPath( urlPath );
		return parsePullRequestsJson( repo, urlPath, jp );
	}

	private static List<PullRequest> parsePullRequestsJson( final Repository repo, final String urlPath,
			final DocumentContext jp ) {
		final List<Integer> pullRequestAmount = jp.read( "$..number" );
		final int numPullRequests = pullRequestAmount.size();
		final List<PullRequest> results = new ArrayList<>( numPullRequests );
		for ( int i = 0; i < numPullRequests; i++ ) {
			final int id = pullRequestAmount.get( i );
			final String source = jp.read( "$.[" + i + "].head.ref" );
			final String destination = jp.read( "$.[" + i + "].base.ref" );
			final String lastUpdate = jp.read( "$.[" + i + "].updated_at" );
			results.add( PullRequest.builder() //
					.id( id ) //
					.repo( repo.getName() ) //
					.source( source ) //
					.destination( destination ) //
					.url( urlPath + "/" + id ) //
					.lastUpdate( lastUpdate ) //
					.build() ); //
		}
		return results;
	}

	private DocumentContext jsonPathForPath( final String urlPath ) {
		final String json = githubTemplate.getForObject( urlPath, String.class );
		return JsonPath.parse( json );
	}

	private void rebase( final Repository repo, final PullRequest pullRequest ) {
		if ( !rebaseService.rebase( repo, pullRequest ) ) {
			addComment( pullRequest );
		}
	}

	private void addComment( final PullRequest pullRequest ) {
		final Map<String, String> request = new HashMap<>();
		request.put( "body", "This pull request needs some manual love ..." );
		githubTemplate.put( pullRequest.getUrl() + "/comments", request, String.class );
	}

}
