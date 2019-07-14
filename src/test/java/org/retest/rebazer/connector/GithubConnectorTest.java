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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.retest.rebazer.service.PullRequestLastUpdateStore;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

class GithubConnectorTest {

	RestTemplate template;
	RebazerConfig config;
	RepositoryConfig repoConfig;
	PullRequest pullRequest;

	GithubConnector cut;

	private static Stream<Arguments> provideTimesForChecksAndIncompleteResults() {
		final String fallbackTime = "2010-01-01T00:00:00Z";
		return Stream.of( //
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"2019-04-04T08:31:30Z\"},{\"completed_at\":\"2019-04-04T08:31:40Z\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}",
						"2019-04-04T08:31:40Z" ), //
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"2019-01-04T15:30:50Z\"},{\"completed_at\":\"2019-04-01T07:30:50Z\"},{\"completed_at\":\"2019-04-10T00:30:50Z\"}]}",
						"2019-04-10T00:30:50Z" ), //
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"2019-05-05T18:00:00Z\"},{\"completed_at\":\"2019-05-05T17:30:50Z\"},{\"completed_at\":\"2019-05-05T17:30:52Z\"}]}",
						"2019-05-05T18:00:00Z" ), //
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":\"2019-04-04T08:31:40Z\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}",
						"2019-04-04T08:31:40Z" ),
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":null},{\"completed_at\":2019-01-04T15:30:50Z}]}",
						"2019-01-04T15:30:50Z" ),
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":null},{\"completed_at\":null}]}",
						fallbackTime ),
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}",
						"2019-04-04T08:31:20Z" ),
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":2019-01-04T15:30:50Z}]}",
						"2019-01-04T15:30:50Z" ),
				Arguments.of(
						"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":\"\"}]}",
						fallbackTime ) );
	}

	private static Stream<Arguments> provideTimesFromDiffrentTimeZones() {
		return Stream.of( //
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T15:00:53Z\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T15:00:50Z\"},{\"completed_at\":\"2019-02-04T15:00:55Z\"},{\"completed_at\":\"2019-02-04T15:00:35Z\"}]}",
						"2019-02-04T15:00:55Z", "2019-02-04T15:00:55Z" ), //
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T15:00:59Z\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T15:00:50Z\"},{\"completed_at\":\"2019-02-04T15:00:55Z\"},{\"completed_at\":\"2019-02-04T15:00:35Z\"}]}",
						"2019-02-04T15:00:55Z", "2019-02-04T15:00:59Z" ), //
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T17:34:59Z\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T07:35:50Z\"},{\"completed_at\":\"2019-02-04T15:23:12Z\"},{\"completed_at\":\"2019-02-04T19:23:30Z\"}]}",
						"2019-02-04T19:23:30Z", "2019-02-04T19:23:30Z" ),
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T20:18:44-04:00\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T16:19:00Z\"},{\"completed_at\":\"2019-02-04T16:18:44Z\"},{\"completed_at\":\"2019-02-04T16:18:59Z\"}]}",
						"2019-02-04T16:19:00Z", "2019-02-05T00:18:44Z" ),
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T16:18:44-04:00\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T20:17:00Z\"},{\"completed_at\":\"2019-02-04T20:17:55Z\"},{\"completed_at\":\"2019-02-04T16:18:50Z\"}]}",
						"2019-02-04T20:17:55Z", "2019-02-04T20:18:44Z" ),
				Arguments.of(
						"{\"updated_at\": \"2019-02-04T16:18:44+01:00\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}",
						"{\"check_runs\":[{\"completed_at\":\"2019-02-04T15:17:00Z\"},{\"completed_at\":\"2019-02-04T15:10:12Z\"},{\"completed_at\":\"2019-02-04T15:18:30Z\"}]}",
						"2019-02-04T15:18:30Z", "2019-02-04T15:18:44Z" ) );
	}

	private static Stream<Arguments> reviewStates() {
		return Stream.of( //
				Arguments.of( null, null, null, false, false ), //
				Arguments.of( null, null, null, true, false ),
				Arguments.of( "APPROVED", "COMMENTED", "COMMENTED", false, true ),
				Arguments.of( "APPROVED", "COMMENTED", null, true, false ),
				Arguments.of( "CHANGES_REQUESTED", "COMMENTED", null, true, false ),
				Arguments.of( "APPROVED", "CHANGES_REQUESTED", "APPROVED", true, false ),
				Arguments.of( "APPROVED", "COMMENTED", null, true, false ),
				Arguments.of( "APPROVED", "APPROVED", "APPROVED", true, true ) );
	}

	private static Stream<Arguments> overrideDifferentStates() {
		return Stream.of( //
				Arguments.of( "[{\"user\": {\"id\": 2}, \"state\": \"COMMENTED\"}]", "COMMENTED" ),
				Arguments.of(
						"[{ \"user\": {\"id\": 2}, \"state\": \"APPROVED\"}, {\"user\": {\"id\": 2}, \"state\": \"COMMENTED\"}]",
						"APPROVED" ),
				Arguments.of(
						"[{ \"user\": {\"id\": 2}, \"state\": \"CHANGES_REQUESTED\"}, {\"user\": {\"id\": 2}, \"state\": \"COMMENTED\"}]",
						"CHANGES_REQUESTED" ),
				Arguments.of(
						"[{ \"user\": {\"id\": 2}, \"state\": \"APPROVED\"}, {\"user\": {\"id\": 2}, \"state\": \"CHANGES_REQUESTED\"}]",
						"CHANGES_REQUESTED" ),
				Arguments.of(
						"[{ \"user\": {\"id\": 2}, \"state\": \"CHANGES_REQUESTED\"}, {\"user\": {\"id\": 2}, \"state\": \"APPROVED\"}]",
						"APPROVED" ),
				Arguments.of(
						"[{ \"user\": {\"id\": 2}, \"state\": \"APPROVED\"}, {\"user\": {\"id\": 2}, \"state\": \"CHANGES_REQUESTED\"}, {\"user\": {\"id\": 2}, \"state\": \"APPROVED\"}]",
						"APPROVED" ) );
	}

	@BeforeEach
	void setUp() {
		template = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		repoConfig = mock( RepositoryConfig.class );
		pullRequest = mock( PullRequest.class );
		final RestTemplateBuilder builder = mock( RestTemplateBuilder.class );
		when( builder.basicAuthentication( any(), any() ) ).thenReturn( builder );
		when( builder.rootUri( anyString() ) ).thenReturn( builder );
		when( builder.build() ).thenReturn( template );
		final Date lastUpdate = PullRequestLastUpdateStore.parseStringToDate( "2010-01-01T00:00:00Z" );
		when( pullRequest.getLastUpdate() ).thenReturn( lastUpdate );

		cut = new GithubConnector( repoConfig, builder );
	}

	@Test
	void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		cut = mock( GithubConnector.class );
		final String head = "12325345923759135";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( head );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isFalse();
	}

	@Test
	void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		cut = mock( GithubConnector.class );
		final String head = "12325345923759135";
		final String lcci = "21342343253253452";
		when( cut.getHeadOfBranch( pullRequest ) ).thenReturn( head );
		when( cut.getLastCommonCommitId( pullRequest ) ).thenReturn( lcci );
		when( cut.rebaseNeeded( pullRequest ) ).thenCallRealMethod();

		assertThat( cut.rebaseNeeded( pullRequest ) ).isTrue();
	}

	@ParameterizedTest
	@MethodSource( "reviewStates" )
	void isApproved_should_handle_all_different_review_states( final String state1, final String state2,
			final String state3, final boolean allRequested, final boolean result ) {
		when( pullRequest.isReviewByAllReviewersRequested() ).thenReturn( allRequested );

		final Map<Integer, String> reviewersState = new HashMap<>();
		reviewersState.put( 1, state1 );
		reviewersState.put( 2, state2 );
		reviewersState.put( 3, state3 );

		when( pullRequest.getReviewers() ).thenReturn( reviewersState );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( "{\"review\": []}" );

		assertThat( cut.isApproved( pullRequest ) ).isEqualTo( result );
	}

	@ParameterizedTest
	@MethodSource( "overrideDifferentStates" )
	void getReviewers_should_provide_always_the_newest_state( final String actions, final String result ) {
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( actions );
		when( pullRequest.getReviewers() ).thenReturn( Maps.newHashMap( 2, null ) );

		cut.isApproved( pullRequest );

		assertThat( pullRequest.getReviewers().get( 2 ) ).isEqualTo( result );
	}

	@Test
	void isApproved_should_ignore_the_creater() {
		final String action = "[{\"user\": {\"id\": 2}, \"state\": \"COMMENTED\"}]";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( action );
		when( pullRequest.getReviewers() ).thenReturn( new HashMap<Integer, String>() );
		when( pullRequest.getCreator() ).thenReturn( 2 );
		cut.isApproved( pullRequest );

		assertThat( pullRequest.getReviewers().get( 2 ) ).isNull();
	}

	@Test
	void greenBuildExists_should_return_false_if_state_is_failed() {
		final String json = "{\"check_runs\":[{\"conclusion\":\"failure\"},{\"conclusion\":\"success\"}]}";
		final String headResponse = "{\"head\":{\"sha\": \"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}";
		final ResponseEntity<String> resp = new ResponseEntity<>( json, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( headResponse );

		assertThat( cut.greenBuildExists( pullRequest ) ).isFalse();
	}

	@Test
	void greenBuildExists_should_return_true_if_state_is_successful() {
		final String json = "{\"check_runs\":[{\"conclusion\":\"success\"},{\"conclusion\":\"success\"}]}";
		final String headResponse = "{\"head\":{\"sha\": \"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}";
		final ResponseEntity<String> resp = new ResponseEntity<>( json, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( headResponse );

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

		final Date lastUpdate =
				PullRequestLastUpdateStore.parseStringToDate( documentContext.read( "$.[0].updated_at" ) );
		final int expectedId = (int) documentContext.read( "$.[0].number" );
		final List<Integer> reviewerId = documentContext.read( "$.[0].requested_reviewers[*].id" );
		final Map<Integer, String> reviewers = Maps.newHashMap( reviewerId.get( 0 ), null );

		final List<PullRequest> expected = Arrays.asList( PullRequest.builder()//
				.id( expectedId )//
				.title( documentContext.read( "$.[0].title" ) ) //
				.creator( documentContext.read( "$.[0].user.id" ) ) //
				.description( documentContext.read( "$.[0].body" ) ) //
				.reviewers( reviewers ) //
				.source( documentContext.read( "$.[0].head.ref" ) ) //
				.destination( documentContext.read( "$.[0].base.ref" ) )//
				.lastUpdate( lastUpdate ).build() );
		final List<PullRequest> actual = cut.getAllPullRequests();

		assertThat( actual ).isEqualTo( expected );
	}

	@Test
	void getLatestUpdate_should_return_updated_PullRequest() {
		final String repositoryTime =
				"{\"updated_at\": \"2019-01-04T15:00:50Z\",\"head\":{\"sha\":\"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime );

		final String checksFinished =
				"{\"check_runs\":[{\"completed_at\":\"2019-01-04T15:30:50Z\"},{\"completed_at\":\"2019-01-04T15:30:59Z\"},{\"completed_at\":\"2019-01-04T15:29:59Z\"}]}";
		final ResponseEntity<String> resp = new ResponseEntity<>( checksFinished, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:30:59Z" );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).hasSameTimeAs( "2019-01-04T15:30:59Z" );
	}

	@ParameterizedTest
	@MethodSource( "provideTimesForChecksAndIncompleteResults" )
	void newestChecksTime_should_return_newest_time_of_the_checks( final String checksFinishedTime,
			final String expected ) {

		final ResponseEntity<String> resp = new ResponseEntity<>( checksFinishedTime, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		final String headResponse = "{\"head\":{\"sha\": \"3ce2b596bcdb72f82425c809f56a0b56f089443e\"}}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( headResponse );

		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( expected );
	}

	@ParameterizedTest
	@MethodSource( "provideTimesFromDiffrentTimeZones" )
	void getLatestUpdate_should_return_always_the_newest_time( final String repositoryTime,
			final String checksFinishedTime, final String expectedChecksTime, final String expectedTime ) {
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime );

		final ResponseEntity<String> resp = new ResponseEntity<>( checksFinishedTime, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( expectedChecksTime );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).hasSameTimeAs( expectedTime );
	}
}
