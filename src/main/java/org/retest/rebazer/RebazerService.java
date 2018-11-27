package org.retest.rebazer;

import static org.retest.rebazer.config.RebazerConfig.POLL_INTERVAL_DEFAULT;
import static org.retest.rebazer.config.RebazerConfig.POLL_INTERVAL_KEY;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.connector.RepositoryConnector;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
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

	private static final String MSG_REBASE_FAILED = "Rebase failed, this pull request needs some manual love ...";

	private final RebaseService rebaseService;
	private final RebazerConfig rebazerConfig;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	private final RestTemplateBuilder templateBuilder;

	@Scheduled( fixedDelayString = "${" + POLL_INTERVAL_KEY + ":" + POLL_INTERVAL_DEFAULT + "}000" )
	public void pollToHandleAllPullRequests() {
		rebazerConfig.getRepos().forEach( repoConfig -> {
			try {
				handleRepo( repoConfig );
			} catch ( final Exception e ) {
				log.error( "Error while handle {}!", repoConfig, e );
			}
		} );
	}

	void handleRepo( final RepositoryConfig repoConfig ) {
		log.info( "Processing {}.", repoConfig );
		final RepositoryConnector repoConnector = repoConfig.getConnector( templateBuilder );
		for ( final PullRequest pullRequest : repoConnector.getAllPullRequests() ) {
			handlePullRequest( repoConnector, repoConfig, pullRequest );
		}
		log.debug( "Processing done for {}.", repoConfig );
	}

	void handlePullRequest( final RepositoryConnector repoConnector, final RepositoryConfig repoConfig,
			final PullRequest pullRequest ) {
		log.debug( "Processing {}.", pullRequest );

		if ( rebazerConfig.isChangeDetection() && pullRequestLastUpdateStore.isHandled( repoConfig, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repoConfig, pullRequest ) );

		} else if ( !repoConnector.greenBuildExists( pullRequest ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repoConfig, pullRequest );

		} else if ( repoConnector.rebaseNeeded( pullRequest ) ) {
			if ( !rebaseService.rebase( repoConfig, pullRequest ) ) {
				repoConnector.addComment( pullRequest, MSG_REBASE_FAILED );
			}
			// we need to update the "lastUpdate" of a PullRequest to counteract if addComment is called
			pullRequestLastUpdateStore.setHandled( repoConfig, repoConnector.getLatestUpdate( pullRequest ) );

		} else if ( !repoConnector.isApproved( pullRequest ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repoConfig, pullRequest );

		} else {
			log.info( "Merging pull request {}.", pullRequest );
			repoConnector.merge( pullRequest );
			pullRequestLastUpdateStore.resetAllInThisRepo( repoConfig );
		}
	}

}
