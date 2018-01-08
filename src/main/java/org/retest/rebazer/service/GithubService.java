package org.retest.rebazer.service;

import java.util.ArrayList;
import java.util.List;

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

	private RestTemplate githubTemplate;

	private RebazerConfig config;

	@Scheduled( fixedDelay = 60 * 1000 )
	public void pollGithub() {
		for ( final Repository repo : config.getRepos() ) {
			log.info( "Processing {}.", repo );
			for ( final PullRequest pr : getAllPullRequests( repo ) ) {
				handlePR( repo, pr );
			}
		}
	}

	private void handlePR( final Repository repo, final PullRequest pullRequest ) {}

	private List<PullRequest> getAllPullRequests( final Repository repo ) {
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

}
