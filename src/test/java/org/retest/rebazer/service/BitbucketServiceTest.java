package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

public class BitbucketServiceTest {

	PullRequestLastUpdateStore pullRequestUpdateStates;
	RestTemplate bitbucketTemplate;
	RebazerConfig config;

	BitbucketService cut;

	@Before
	public void setUp() {
		bitbucketTemplate = mock( RestTemplate.class );
		final RestTemplate bitbucketLegacyTemplate = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		final RebaseService rebaseService = mock( RebaseService.class );
		pullRequestUpdateStates = mock( PullRequestLastUpdateStore.class );

		cut = new BitbucketService( bitbucketTemplate, bitbucketLegacyTemplate, config, rebaseService,
				pullRequestUpdateStates );
	}

	@Test
	public void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final BitbucketService cut = mock( BitbucketService.class );
		final String head = "12325345923759135";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( head );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isFalse();
	}

	@Test
	public void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final BitbucketService cut = mock( BitbucketService.class );
		final String head = "12325345923759135";
		final String lcci = "21342343253253452";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( lcci );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isTrue();
	}

	@Test
	public void isApproved_should_return_false_if_approved_is_false() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{participants: [{\"approved\": false}]}\"";
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest ) ).isFalse();
	}

	@Test
	public void isApproved_should_return_ture_if_approved_is_true() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{participants: [{\"approved\": true}]}\"";
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.isApproved( pullRequest ) ).isTrue();
	}

	@Test
	public void greenBuildExists_should_return_false_if_state_is_failed() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{values: [{\"state\": FAILED}]}";
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isFalse();
	}

	@Test
	public void greenBuildExists_should_return_true_if_state_is_successful() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{values: [{\"state\": SUCCESSFUL}]}";
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isTrue();
	}

	@Test
	public void getAllPullRequests_should_return_all_pull_requests_as_list() throws Exception {
		final Repository repo = mock( Repository.class );
		final String json = new String( Files.readAllBytes(
				Paths.get( "src/test/resources/org/retest/rebazer/service/bitbucketservicetest/response.json" ) ) );
		final DocumentContext documentContext = JsonPath.parse( json );
		when( config.getTeam() ).thenReturn( "test_team" );
		when( repo.getName() ).thenReturn( "test_repo_name" );
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		final int expectedId = (int) documentContext.read( "$.values[0].id" );
		final String expectedUrl =
				"/repositories/" + config.getTeam() + "/" + repo.getName() + "/pullrequests/" + expectedId;
		final List<PullRequest> expected = Arrays.asList( PullRequest.builder().id( expectedId ).repo( repo.getName() )
				.source( documentContext.read( "$.values[0].source.branch.name" ) )
				.destination( documentContext.read( "$.values[0].destination.branch.name" ) ).url( expectedUrl )
				.lastUpdate( documentContext.read( "$.values[0].updated_on" ) ).build() );
		final List<PullRequest> actual = cut.getAllPullRequests( repo );

		assertThat( actual ).isEqualTo( expected );
	}

	@Test
	public void getLatestUpdate_should_return_updated_PullRequest() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{\"updated_on\": \"someTimestamp\"}";
		when( bitbucketTemplate.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "someTimestamp" );
	}

}
