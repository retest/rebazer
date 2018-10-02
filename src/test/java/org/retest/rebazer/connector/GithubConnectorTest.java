package org.retest.rebazer.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

class GithubConnectorTest {

	RestTemplate template;
	RebazerConfig config;
	RepositoryConfig repoConfig;

	GithubConnector cut;

	@BeforeEach
	void setUp() {
		template = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		repoConfig = mock( RepositoryConfig.class );
		final RestTemplateBuilder builder = mock( RestTemplateBuilder.class );
		when( builder.basicAuthorization( any(), any() ) ).thenReturn( builder );
		when( builder.rootUri( anyString() ) ).thenReturn( builder );
		when( builder.build() ).thenReturn( template );

		cut = new GithubConnector( repoConfig, builder );
	}

	@Test
	void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		cut = mock( GithubConnector.class );
		final String head = "12325345923759135";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( head );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isFalse();
	}

	@Test
	void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		cut = mock( GithubConnector.class );
		final String head = "12325345923759135";
		final String lcci = "21342343253253452";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( lcci );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isTrue();
	}

	@Test
	void isApproved_should_return_false_if_approved_is_false() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{review: [{\"state\": \"CHANGES_REQUESTED\"}]}\"";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest ) ).isFalse();
	}

	@Test
	void isApproved_should_return_true_if_approved_is_true() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{review: [{\"state\": \"APPROVED\"}]}\"";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest ) ).isTrue();
	}

	@Test
	void greenBuildExists_should_return_false_if_state_is_failed() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{statuses: [{\"state\": \"failure_or_error\"}]}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isFalse();
	}

	@Test
	void greenBuildExists_should_return_true_if_state_is_successful() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{statuses: [{\"state\": \"success\"}]}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isTrue();
	}

	@Test
	void getAllPullRequests_should_return_all_pull_requests_as_list() throws Exception {
		final String json = new String( Files.readAllBytes(
				Paths.get( "src/test/resources/org/retest/rebazer/service/githubservicetest/response.json" ) ) );
		final DocumentContext documentContext = JsonPath.parse( json );
		when( repoConfig.getTeam() ).thenReturn( "test_team" );
		when( repoConfig.getRepo() ).thenReturn( "test_repo_name" );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		final int expectedId = (int) documentContext.read( "$.[0].number" );
		final List<PullRequest> expected =
				Arrays.asList( PullRequest.builder().id( expectedId ).source( documentContext.read( "$.[0].head.ref" ) )
						.destination( documentContext.read( "$.[0].base.ref" ) )
						.lastUpdate( documentContext.read( "$.[0].updated_at" ) ).build() );
		final List<PullRequest> actual = cut.getAllPullRequests();

		assertThat( actual ).isEqualTo( expected );
	}

	@Test
	void getLatestUpdate_should_return_updated_PullRequest() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{\"updated_at\": \"someTimestamp\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "someTimestamp" );
	}

}
