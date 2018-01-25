package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class GithubServiceTest {

	RestTemplate githubTemplate;
	RebazerConfig config;
	Team team;

	GithubService cut;

	@Before
	public void setUp() {
		githubTemplate = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		team = mock( Team.class );
		final RebaseService rebaseService = mock( RebaseService.class );

		cut = new GithubService( rebaseService );
	}

	@Test
	public void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final GithubService cut = mock( GithubService.class );
		final String head = "12325345923759135";
		when( cut.getHeadOfBranch( pullRequest, team, githubTemplate ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest, githubTemplate ) ).thenReturn( head );
		when( cut.rebaseNeeded( pullRequest, team, githubTemplate ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest, team, githubTemplate ) ).isFalse();
	}

	@Test
	public void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final GithubService cut = mock( GithubService.class );
		final String head = "12325345923759135";
		final String lcci = "21342343253253452";
		when( cut.getHeadOfBranch( pullRequest, team, githubTemplate ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest, githubTemplate ) ).thenReturn( lcci );
		when( cut.rebaseNeeded( pullRequest, team, githubTemplate ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest, team, githubTemplate ) ).isTrue();
	}

	@Test
	public void isApproved_should_return_false_if_approved_is_false() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{review: [{\"state\": \"CHANGES_REQUESTED\"}]}\"";
		when( githubTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest, githubTemplate ) ).isFalse();
	}

	@Test
	public void isApproved_should_return_true_if_approved_is_true() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{review: [{\"state\": \"APPROVED\"}]}\"";
		when( githubTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest, githubTemplate ) ).isTrue();
	}

	@Test
	public void greenBuildExists_should_return_false_if_state_is_failed() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{statuses: [{\"state\": \"failure_or_error\"}]}";
		when( githubTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest, team, githubTemplate ) ).isFalse();
	}

	@Test
	public void greenBuildExists_should_return_true_if_state_is_successful() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{statuses: [{\"state\": \"success\"}]}";
		when( githubTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest, team, githubTemplate ) ).isTrue();
	}

	@Test
	public void getAllPullRequests_should_return_all_pull_requests_as_list() throws IOException {
		final RepositoryConfig repo = mock( RepositoryConfig.class );
		final String json = new String( Files.readAllBytes(
				Paths.get( "src/test/resources/org/retest/rebazer/service/githubservicetest/response.json" ) ) );
		final DocumentContext documentContext = JsonPath.parse( json );
		when( team.getName() ).thenReturn( "test_team" );
		when( repo.getName() ).thenReturn( "test_repo_name" );
		when( githubTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		final int expectedId = (int) documentContext.read( "$.[0].number" );
		final String expectedUrl = "/repos/" + team.getName() + "/" + repo.getName() + "/pulls/" + expectedId;
		final List<PullRequest> expected = Arrays.asList( PullRequest.builder().id( expectedId ).repo( repo.getName() )
				.source( documentContext.read( "$.[0].head.ref" ) )
				.destination( documentContext.read( "$.[0].base.ref" ) ).url( expectedUrl )
				.lastUpdate( documentContext.read( "$.[0].updated_at" ) ).build() );
		final List<PullRequest> actual = cut.getAllPullRequests( repo, team, githubTemplate );

		assertThat( actual ).isEqualTo( expected );
	}

}
