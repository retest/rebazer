package org.retest.rebazer.service;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.config.RebazerConfig.Services;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor( onConstructor = @__( @Autowired ) )
public class HandleServices {

	private final RebazerConfig config;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	@Qualifier( "bitbucketService" )
	private final Provider bitbucketService;

	@Qualifier( "githubService" )
	private final Provider githubService;

	@Scheduled( fixedDelay = 60 * 1000 )
	public void pollBitbucket() {
		for ( final Services service : config.getServices() ) {
			if ( "bitbucket".equals( service.getType() ) ) {
				service.setProvider( bitbucketService );
				final Team team = service.getTeam();
				for ( final Repository repo : team.getRepos() ) {
					log.debug( "Processing {}.", repo );
					for ( final PullRequest pr : bitbucketService.getAllPullRequests( repo, team ) ) {
						System.out.println( pr );
						handlePR( bitbucketService, repo, pr, team );
					}
				}
			}
		}

	}

	private void handlePR( final Provider provider, final Repository repo, final PullRequest pullRequest,
			final Team team ) {
		log.debug( "Processing {}.", pullRequest );

		if ( pullRequestLastUpdateStore.isHandled( repo, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repo, pullRequest ) );

		} else if ( !provider.greenBuildExists( pullRequest, team ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repo, pullRequest );

		} else if ( provider.rebaseNeeded( pullRequest, team ) ) {
			provider.rebase( repo, pullRequest, team );
			// we need to update the "lastUpdate" of a PullRequest to counteract if addComment is called
			pullRequestLastUpdateStore.setHandled( repo, provider.getLatestUpdate( pullRequest ) );

		} else if ( !provider.isApproved( pullRequest ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repo, pullRequest );

		} else {
			log.info( "Merging pull request " + pullRequest );
			provider.merge( pullRequest );
			pullRequestLastUpdateStore.resetAllInThisRepo( repo );
		}
	}

}
