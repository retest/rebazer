package org.retest.rebazer;

import java.net.MalformedURLException;
import java.net.URL;

import org.retest.rebazer.connector.BitbucketConnector;
import org.retest.rebazer.connector.GithubConnector;
import org.retest.rebazer.connector.RepositoryConnector;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;

import lombok.Getter;
import lombok.SneakyThrows;

public enum RepositoryHostingTypes {

	BITBUCKET( "https://bitbucket.org", "https://api.bitbucket.org" ),
	GITHUB( "https://github.com", "https://api.github.com" ),

	;

	@Getter
	final URL defaultGitHost;
	@Getter
	final URL defaultApiHost;

	@SneakyThrows( MalformedURLException.class )
	private RepositoryHostingTypes( final String defaultGitHostUrl, final String defaultApiHostUrl ) {
		defaultGitHost = new URL( defaultGitHostUrl );
		defaultApiHost = new URL( defaultApiHostUrl );
	}

	public RepositoryConnector getConnector( final RepositoryConfig repoConfig,
			final RestTemplateBuilder templateBuilder ) {
		switch ( this ) {
			case BITBUCKET:
				return new BitbucketConnector( repoConfig, templateBuilder );
			case GITHUB:
				return new GithubConnector( repoConfig, templateBuilder );
			default:
				throw new RuntimeException( "No Repository defined for provider: " + this );
		}
	}

}
