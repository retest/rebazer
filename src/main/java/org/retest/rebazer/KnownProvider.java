package org.retest.rebazer;

import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryTeam;
import org.retest.rebazer.connector.BitbucketConnector;
import org.retest.rebazer.connector.GithubConnector;
import org.retest.rebazer.connector.RepositoryConnector;
import org.springframework.boot.web.client.RestTemplateBuilder;

public enum KnownProvider {

	BITBUCKET,
	GITHUB;

	public RepositoryConnector getRepository( final RepositoryTeam team, final RepositoryConfig repo,
			final RestTemplateBuilder builder ) {
		switch ( this ) {
			case BITBUCKET:
				return new BitbucketConnector( team, repo, builder );
			case GITHUB:
				return new GithubConnector( team, repo, builder );
			default:
				throw new RuntimeException( "No Repository defined for provider: " + this );
		}
	}

}
