package org.retest.rebazer.service;

import org.retest.rebazer.config.BitbucketConfig;
import org.retest.rebazer.config.GithubConfig;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryHost;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor( onConstructor = @__( @Autowired ) )
public class HandleServices {

	private final RebaseService rebaseService;
	private final RebazerConfig config;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	private final BitbucketConfig bitbucketConfig = new BitbucketConfig();
	private final GithubConfig githubConfig = new GithubConfig();
	private final RestTemplateBuilder builder;
	private Provider provider;

	@Scheduled( fixedDelayString = "${rebazer.pollInterval:60}000" )
	public void pollBitbucket() {
		for ( final RepositoryHost hosts : config.getHosts() ) {
			for ( final Team team : hosts.getTeam() ) {
				for ( final RepositoryConfig repo : team.getRepos() ) {
					log.debug( "Processing {}.", repo );
					final KnownProvider knownProvider = KnownProvider.valueOf( hosts.getType() );
					switch ( knownProvider ) {
						case BITBUCKET:
							final RestTemplate bitbucketLegacyTemplate =
									bitbucketConfig.bitbucketLegacyTemplate( builder, team.getUser(), team.getPass() );
							final RestTemplate bitbucketTemplate =
									bitbucketConfig.bitbucketTemplate( builder, team.getUser(), team.getPass() );
							provider = new BitbucketService( rebaseService, team, repo, bitbucketLegacyTemplate,
									bitbucketTemplate );
							break;
						case GITHUB:
							final RestTemplate githubTemplate =
									githubConfig.githubTemplate( builder, team.getUser(), team.getPass() );
							provider = new GithubService( rebaseService, team, repo, githubTemplate );
							break;
						default:
							log.info( "The hosting Service via: {} is not supported", hosts.getType() );
					}
					for ( final PullRequest pr : provider.getAllPullRequests( repo ) ) {
						handlePR( provider, repo, pr );
					}
				}
			}
		}

	}

	public void handlePR( final Provider provider, final RepositoryConfig repositories,
			final PullRequest pullRequest ) {
		log.debug( "Processing {}.", pullRequest );

		if ( pullRequestLastUpdateStore.isHandled( repositories, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repositories, pullRequest ) );

		} else if ( !provider.greenBuildExists( pullRequest ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repositories, pullRequest );

		} else if ( provider.rebaseNeeded( pullRequest ) ) {
			provider.rebase( repositories, pullRequest );
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
