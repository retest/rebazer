package org.retest.rebazer.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.retest.rebazer.service.PullRequestLastUpdateStore;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

class BitbucketConnectorTest {

	PullRequestLastUpdateStore pullRequestUpdateStates;
	RestTemplate template;
	RebazerConfig config;
	RepositoryConfig repoConfig;

	BitbucketConnector cut;

	private static Stream<Arguments> reviewStates() {
		return Stream.of( //
				Arguments.of( "{participants: []}", false, false ), //
				Arguments.of( "{participants: []}", true, false ), //
				Arguments.of( "{participants: [{\"approved\": false}, {\"approved\": false}]}", false, false ),
				Arguments.of( "{participants: [{\"approved\": false}, {\"approved\": false}]}", true, false ),
				Arguments.of( "{participants: [{\"approved\": false}, {\"approved\": true}]}", false, true ),
				Arguments.of( "{participants: [{\"approved\": false}, {\"approved\": true}]}", true, false ),
				Arguments.of( "{participants: [{\"approved\": true}, {\"approved\": false}]}", true, false ),
				Arguments.of( "{participants: [{\"approved\": true}, {\"approved\": true}]}", true, true ) );
	}

	@BeforeEach
	void setUp() {
		template = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		repoConfig = mock( RepositoryConfig.class );
		final RestTemplateBuilder builder = mock( RestTemplateBuilder.class );
		when( builder.basicAuthentication( any(), any() ) ).thenReturn( builder );
		when( builder.rootUri( anyString() ) ).thenReturn( builder );
		when( builder.build() ).thenReturn( template );
		pullRequestUpdateStates = mock( PullRequestLastUpdateStore.class );

		cut = new BitbucketConnector( repoConfig, builder );
	}

	@Test
	void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		cut = mock( BitbucketConnector.class );
		final String head = "12325345923759135";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastParentCommitId( pullRequest ) ).thenReturn( head );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isFalse();
	}

	@Test
	void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		final PullRequest pullRequest = mock( PullRequest.class );
		cut = mock( BitbucketConnector.class );
		final String head = "12325345923759135";
		final String lcci = "21342343253253452";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastParentCommitId( pullRequest ) ).thenReturn( lcci );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isTrue();
	}

	@ParameterizedTest
	@MethodSource( "reviewStates" )
	void isApproved_should_handle_all_different_review_states( final String states, final boolean allRequested,
			final boolean result ) {
		final PullRequest pullRequest = mock( PullRequest.class );
		when( pullRequest.isReviewByAllReviewersRequested() ).thenReturn( allRequested );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( states );

		assertThat( cut.isApproved( pullRequest ) ).isEqualTo( result );
	}

	@Test
	void greenBuildExists_should_return_false_if_state_is_failed() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{values: [{\"state\": FAILED}]}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isFalse();
	}

	@Test
	void greenBuildExists_should_return_true_if_state_is_successful() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{values: [{\"state\": SUCCESSFUL}]}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		assertThat( cut.greenBuildExists( pullRequest ) ).isTrue();
	}

	@Test
	void getAllPullRequests_should_return_all_pull_requests_as_list() throws Exception {
		final String json = new String( Files.readAllBytes(
				Paths.get( "src/test/resources/org/retest/rebazer/service/bitbucketservicetest/response.json" ) ) );
		final DocumentContext documentContext = JsonPath.parse( json );

		when( repoConfig.getTeam() ).thenReturn( "test_team" );
		when( repoConfig.getRepo() ).thenReturn( "test_repo_name" );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );
		final Date lastUpdate =
				PullRequestLastUpdateStore.parseStringToDate( documentContext.read( "$.values[0].updated_on" ) );
		final int expectedId = (int) documentContext.read( "$.values[0].id" );
		final List<PullRequest> expected = Arrays.asList( PullRequest.builder()//
				.id( expectedId )//
				.title( documentContext.read( "$.values[0].title" ) )
				.description( documentContext.read( "$.values[0].description" ) )
				.source( documentContext.read( "$.values[0].source.branch.name" ) )//
				.destination( documentContext.read( "$.values[0].destination.branch.name" ) )//
				.lastUpdate( lastUpdate )//
				.build() );
		final List<PullRequest> actual = cut.getAllPullRequests();

		assertThat( actual ).isEqualTo( expected );
	}

	@Test
	void getLatestUpdate_should_return_updated_PullRequest() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String json = "{\"updated_on\": \"2019-02-04T20:18:44Z\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( json );

		cut.getLatestUpdate( pullRequest );

		final ArgumentCaptor<Date> parsedDate = ArgumentCaptor.forClass( Date.class );
		verify( pullRequest ).updateLastChange( parsedDate.capture() );
		assertThat( parsedDate.getValue() ).isEqualTo( "2019-02-04T20:18:44Z" );

	}
}
