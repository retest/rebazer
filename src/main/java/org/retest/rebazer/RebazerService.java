package org.retest.rebazer;

import static org.retest.rebazer.config.RebazerConfig.POLL_INTERVAL_DEFAULT;
import static org.retest.rebazer.config.RebazerConfig.POLL_INTERVAL_KEY;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryHost;
import org.retest.rebazer.config.RebazerConfig.RepositoryTeam;
import org.retest.rebazer.connector.RepositoryConnector;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.service.PullRequestLastUpdateStore;
import org.retest.rebazer.service.RebaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor( onConstructor = @__( @Autowired ) )
public class RebazerService {

	private final RebaseService rebaseService;
	private final RebazerConfig config;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	private final RestTemplateBuilder builder;

	@Scheduled( fixedDelayString = "${" + POLL_INTERVAL_KEY + ":" + POLL_INTERVAL_DEFAULT + "}000" )
	public void pollToHandleAllPullRequests() {
		config.getHosts().forEach( host -> {
			host.getTeams().forEach( team -> {
				team.getRepos().forEach( repoConfig -> {
					handleRepo( host, team, repoConfig );
				} );
			} );
		} );
	}

	private void handleRepo( final RepositoryHost host, final RepositoryTeam team, final RepositoryConfig repoConfig ) {
		log.debug( "Processing {}.", repoConfig );
		final RepositoryConnector repoConnector = host.getType().getRepository( team, repoConfig, builder );
		for ( final PullRequest pullRequest : repoConnector.getAllPullRequests( repoConfig ) ) {
			handlePullRequest( repoConnector, repoConfig, pullRequest );
		}
		log.debug( "Processing done for {}.", repoConfig );
	}

	public void handlePullRequest( final RepositoryConnector provider, final RepositoryConfig repoConfig,
			final PullRequest pullRequest ) {
		log.debug( "Processing {}.", pullRequest );

		if ( pullRequestLastUpdateStore.isHandled( repoConfig, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repoConfig, pullRequest ) );

		} else if ( !provider.greenBuildExists( pullRequest ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repoConfig, pullRequest );

		} else if ( provider.rebaseNeeded( pullRequest ) ) {
			if ( !rebaseService.rebase( repoConfig, pullRequest ) ) {
				provider.addComment( pullRequest );
			}
			// we need to update the "lastUpdate" of a PullRequest to counteract if addComment is called
			pullRequestLastUpdateStore.setHandled( repoConfig, provider.getLatestUpdate( pullRequest ) );

		} else if ( !provider.isApproved( pullRequest ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repoConfig, pullRequest );

		} else {
			log.info( "Merging pull request " + pullRequest );
			provider.merge( pullRequest );
			pullRequestLastUpdateStore.resetAllInThisRepo( repoConfig );
		}
	}

}
