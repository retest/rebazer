package org.retest.rebazer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;

import org.junit.jupiter.api.Test;
import org.retest.rebazer.connector.BitbucketConnector;
import org.retest.rebazer.connector.GithubConnector;
import org.retest.rebazer.connector.RepositoryConnector;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;

class RepositoryHostingTypesTest {
	@Test
	void bitbucket_type_should_result_in_default_bitbucket_url() throws Exception {
		final RepositoryHostingTypes repositoryHostingTypes = RepositoryHostingTypes.BITBUCKET;
		final URL bitbucketURL = new URL( "https://bitbucket.org" );
		assertThat( repositoryHostingTypes.defaultGitHost ).isEqualTo( bitbucketURL );
	}

	@Test
	void github_type_should_result_in_default_github_url() throws Exception {
		final RepositoryHostingTypes repositoryHostingTypes = RepositoryHostingTypes.GITHUB;
		final URL githubURL = new URL( "https://github.com" );
		assertThat( repositoryHostingTypes.defaultGitHost ).isEqualTo( githubURL );
	}

	@Test
	void bitbucket_type_should_return_bitbucket_connector() {
		final RepositoryHostingTypes repositoryHostingTypes = RepositoryHostingTypes.BITBUCKET;
		final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		final RepositoryConfig repositoryConfig = mock( RepositoryConfig.class );
		when( repositoryConfig.getUser() ).thenReturn( "user" );
		when( repositoryConfig.getPass() ).thenReturn( "password" );
		final RepositoryConnector connector =
				repositoryHostingTypes.getConnector( repositoryConfig, restTemplateBuilder );
		assertThat( connector ).isInstanceOf( BitbucketConnector.class );
	}

	@Test
	void github_type_should_return_github_connector() {
		final RepositoryHostingTypes repositoryHostingTypes = RepositoryHostingTypes.GITHUB;
		final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		final RepositoryConfig repositoryConfig = mock( RepositoryConfig.class );
		when( repositoryConfig.getUser() ).thenReturn( "user" );
		when( repositoryConfig.getPass() ).thenReturn( "password" );
		final RepositoryConnector connector =
				repositoryHostingTypes.getConnector( repositoryConfig, restTemplateBuilder );
		assertThat( connector ).isInstanceOf( GithubConnector.class );
	}
}
