package org.retest.rebazer.service;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryHost;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor( onConstructor = @__( @Autowired ) )
public class HandleServices {

	private final RebaseService rebaseService;
	private final RebazerConfig config;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	private final RestTemplateBuilder builder;
	private Repository provider;

	@Scheduled( fixedDelayString = "${" + RebazerConfig.POLL_INTERVAL_KEY + ":" + RebazerConfig.POLL_INTERVAL_DEFAULT
			+ "}000" )
	public void pollToHandleAllPullRequests() {
		config.getHosts().forEach( host -> {
			host.getTeams().forEach( team -> {
				team.getRepos().forEach( repo -> {
					handleRepo( host, team, repo );
				} );
			} );
		} );
	}

	private void handleRepo( final RepositoryHost host, final Team team, final RepositoryConfig repo ) {
		log.debug( "Processing {}.", repo );
		switch ( host.getType() ) {
			case BITBUCKET:
				provider = new BitbucketService( team, repo, builder );
				break;
			case GITHUB:
				provider = new GithubService( team, repo, builder );
				break;
			default:
				log.info( "The hosting Service via: {} is not supported", host.getType() );
		}
		for ( final PullRequest pr : provider.getAllPullRequests( repo ) ) {
			handlePR( provider, repo, pr );
		}
		log.debug( "Processing done for {}.", repo );
	}

	public void handlePR( final Repository provider, final RepositoryConfig repositories,
			final PullRequest pullRequest ) {
		log.debug( "Processing {}.", pullRequest );

		if ( pullRequestLastUpdateStore.isHandled( repositories, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repositories, pullRequest ) );

		} else if ( !provider.greenBuildExists( pullRequest ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repositories, pullRequest );

		} else if ( provider.rebaseNeeded( pullRequest ) ) {
			if ( !rebaseService.rebase( repositories, pullRequest ) ) {
				provider.addComment( pullRequest );
			}
			// we need to update the "lastUpdate" of a PullRequest to counteract if addComment is called
			pullRequestLastUpdateStore.setHandled( repositories, provider.getLatestUpdate( pullRequest ) );

		} else if ( !provider.isApproved( pullRequest ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repositories, pullRequest );

		} else {
			log.info( "Merging pull request " + pullRequest );
			provider.merge( pullRequest );
			pullRequestLastUpdateStore.resetAllInThisRepo( repositories );
		}
	}

}
