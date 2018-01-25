package org.retest.rebazer.service;

import java.util.HashMap;
import java.util.Map;

import org.retest.rebazer.config.BitbucketConfig;
import org.retest.rebazer.config.GithubConfig;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.config.RebazerConfig.RepositoryHost;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

	private final RebazerConfig config;
	private final PullRequestLastUpdateStore pullRequestLastUpdateStore;

	@Qualifier( "bitbucketService" )
	private final Provider bitbucketService;

	@Qualifier( "githubService" )
	private final Provider githubService;

	private final BitbucketConfig bitbucketConfig = new BitbucketConfig();
	private final GithubConfig githubConfig = new GithubConfig();
	private final RestTemplateBuilder builder;

	private final Map<String, RestTemplate> restTemplates = new HashMap<>();
	private RestTemplate template;

	@Scheduled( fixedDelay = 60 * 1000 )
	public void pollBitbucket() {
		for ( final RepositoryHost hosts : config.getHosts() ) {
			final Team team = hosts.getTeam();
			if ( "bitbucket".equals( hosts.getType() ) ) {
				hosts.setProvider( bitbucketService );
				restTemplates.put( team.getUser(),
						bitbucketConfig.bitbucketLegacyTemplate( builder, team.getUser(), team.getPass() ) );
				restTemplates.put( team.getName(),
						bitbucketConfig.bitbucketTemplate( builder, team.getUser(), team.getPass() ) );
			} else if ( "github".equals( hosts.getType() ) ) {
				hosts.setProvider( githubService );
				restTemplates.put( team.getName(),
						githubConfig.githubTemplate( builder, team.getUser(), team.getPass() ) );
			}
			for ( final Repository repo : team.getRepos() ) {
				log.debug( "Processing {}.", repo );
				for ( final PullRequest pr : hosts.getProvider().getAllPullRequests( repo, team,
						restTemplates.get( team.getName() ) ) ) {
					handlePR( hosts.getProvider(), repo, pr, team, restTemplates );
				}
			}
		}

	}

	public void handlePR( final Provider provider, final Repository repo, final PullRequest pullRequest,
			final Team team, final Map<String, RestTemplate> restTemplates ) {
		log.debug( "Processing {}.", pullRequest );

		if ( provider.getClass().equals( BitbucketService.class ) ) {
			template = restTemplates.get( team.getUser() );
		} else if ( provider.getClass().equals( GithubService.class ) ) {
			template = restTemplates.get( team.getName() );
		}
		if ( pullRequestLastUpdateStore.isHandled( repo, pullRequest ) ) {
			log.info( "{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestLastUpdateStore.getLastDate( repo, pullRequest ) );

		} else if ( !provider.greenBuildExists( pullRequest, team, restTemplates.get( team.getName() ) ) ) {
			log.info( "Waiting for green build of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repo, pullRequest );

		} else if ( provider.rebaseNeeded( pullRequest, team, template ) ) {
			provider.rebase( repo, pullRequest, team, restTemplates.get( team.getName() ) );
			// we need to update the "lastUpdate" of a PullRequest to counteract if addComment is called
			pullRequestLastUpdateStore.setHandled( repo, provider.getLatestUpdate( pullRequest, template ) );

		} else if ( !provider.isApproved( pullRequest, restTemplates.get( team.getName() ) ) ) {
			log.info( "Waiting for approval of {}.", pullRequest );
			pullRequestLastUpdateStore.setHandled( repo, pullRequest );

		} else {
			log.info( "Merging pull request " + pullRequest );
			provider.merge( pullRequest, restTemplates.get( team.getName() ) );
			pullRequestLastUpdateStore.resetAllInThisRepo( repo );
		}
	}

	public int getCurrentPollInterval( final RebazerConfig config ) {
		return config.getPollInterval();
	}

}
