package org.retest.rebazer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.retest.rebazer.RepositoryHostingTypes;
import org.retest.rebazer.connector.GithubConnector;
import org.retest.rebazer.connector.RepositoryConnector;
import org.springframework.boot.web.client.RestTemplateBuilder;

class RepositoryConfigTest {

	private RepositoryConfig repositoryConfig;

	@BeforeAll
	void setUp() throws Exception {
		repositoryConfig = new RepositoryConfig( RepositoryHostingTypes.GITHUB,
				new URL( RepositoryHostingTypes.GITHUB.getDefaultUrl().toString() ), "team", "repository", "user",
				"pass", "master" );
	}

	@Test
	void toString_should_return_correct_string() {
		assertThat( repositoryConfig.toString() ).isEqualTo( "Repo [ github.com/team/repository ]" );
	}

	@Test
	void getURL_should_return_correct_url() {
		assertThat( repositoryConfig.getUrl() ).isEqualTo( "https://github.com/team/repository.git" );
	}

	@Test
	void getQualifiers_should_return_correct_qualifiers() {
		assertThat( repositoryConfig.getQualifiers() )
				.containsExactly( RepositoryHostingTypes.GITHUB.getDefaultUrl().getHost(), "team", "repository" );

	}

	@Test
	void getConnector_should_return_correct_connector() {
		final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
		final RepositoryConnector connector = repositoryConfig.getConnector( restTemplateBuilder );
		assertThat( connector ).isInstanceOf( GithubConnector.class );

	}
}
