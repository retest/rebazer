package org.retest.rebazer;

import java.net.MalformedURLException;
import java.net.URL;

import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryTeam;
import org.retest.rebazer.connector.BitbucketConnector;
import org.retest.rebazer.connector.GithubConnector;
import org.retest.rebazer.connector.RepositoryConnector;
import org.springframework.boot.web.client.RestTemplateBuilder;

import lombok.Getter;
import lombok.SneakyThrows;

public enum KnownProvider {

	BITBUCKET( "https://bitbucket.org/" ),
	GITHUB( "https://github.com/" ),

	;

	@Getter
	final URL defaultUrl;

	@SneakyThrows( MalformedURLException.class )
	private KnownProvider( final String defaultUrl ) {
		this.defaultUrl = new URL( defaultUrl );
	}

	public RepositoryConnector getRepository( final RepositoryTeam repoTeam, final RepositoryConfig repoConfig,
			final RestTemplateBuilder templateBuilder ) {
		switch ( this ) {
			case BITBUCKET:
				return new BitbucketConnector( repoTeam, repoConfig, templateBuilder );
			case GITHUB:
				return new GithubConnector( repoTeam, repoConfig, templateBuilder );
			default:
				throw new RuntimeException( "No Repository defined for provider: " + this );
		}
	}

}
