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

	GithubConnector cut;

	@BeforeEach
	void setUp() {
		template = mock( RestTemplate.class );
		config = mock( RebazerConfig.class );
		repoConfig = mock( RepositoryConfig.class );
		final RestTemplateBuilder builder = mock( RestTemplateBuilder.class );
		when( builder.basicAuthentication( any(), any() ) ).thenReturn( builder );
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
		final PullRequest pullRequest = mock( PullRequest.class );
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
		final String repositoryTime = "{\"updated_at\": \"2019-01-04T15:00:50Z\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime );
		final String checksFinished =
				"{\"check_runs\":[{\"completed_at\":\"2019-01-04T15:30:50Z\"},{\"completed_at\":\"2019-01-04T15:30:59Z\"},{\"completed_at\":\"2019-01-04T15:29:59Z\"}]}";

		final ResponseEntity<String> resp = new ResponseEntity<>( checksFinished, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:30:59Z" );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "2019-01-04T15:30:59Z" );

	}

	@Test
	void newestChecksTime_should_return_newest_time_of_the_checks() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String checksFinished1 =
				"{\"check_runs\":[{\"completed_at\":\"2019-04-04T08:31:30Z\"},{\"completed_at\":\"2019-04-04T08:31:40Z\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}";

		final ResponseEntity<String> resp = new ResponseEntity<>( checksFinished1, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-04-04T08:31:30Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-04-04T08:31:40Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-04-04T08:31:20" );

		final String checksFinished2 =
				"{\"check_runs\":[{\"completed_at\":\"2019-01-04T15:30:50Z\"},{\"completed_at\":\"2019-04-01T07:30:50Z\"},{\"completed_at\":\"2019-04-10T00:30:50Z\"}]}";
		final ResponseEntity<String> resp2 = new ResponseEntity<>( checksFinished2, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp2 );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-01-04T15:30:50Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-04-01T07:30:50Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-04-10T00:30:50Z" );

		final String checksFinished3 =
				"{\"check_runs\":[{\"completed_at\":\"2019-05-05T18:00:00Z\"},{\"completed_at\":\"2019-05-05T17:30:50Z\"},{\"completed_at\":\"2019-05-05T17:30:52Z\"}]}";
		final ResponseEntity<String> resp3 = new ResponseEntity<>( checksFinished3, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp3 );

		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-05-05T18:00:00Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-05-05T17:30:50Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-05-05T17:30:52Z" );

	}

	@Test
	void newestChecksTime_should_return_nonNull_values() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String only2CheckTimesAvailable =
				"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":\"2019-04-04T08:31:40Z\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}";

		final ResponseEntity<String> resp = new ResponseEntity<>( only2CheckTimesAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-04-04T08:31:20" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-04-04T08:31:40Z" );

		final String only1CheckTimeAvailable =
				"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":null},{\"completed_at\":2019-01-04T15:30:50Z}]}";
		final ResponseEntity<String> resp2 = new ResponseEntity<>( only1CheckTimeAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp2 );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:30:50Z" );

		final String noCheckTimeAvailable =
				"{\"check_runs\":[{\"completed_at\":null},{\"completed_at\":null},{\"completed_at\":null}]}";

		final ResponseEntity<String> resp3 = new ResponseEntity<>( noCheckTimeAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp3 );

		when( pullRequest.getLastUpdate() ).thenReturn( "2019-01-04T15:00:50Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:00:50Z" );

	}

	@Test
	void newestCbecksTime_should_return_nonEmpty_values() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String only2CheckTimesAvailable =
				"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":\"2019-04-04T08:31:20Z\"}]}";

		final ResponseEntity<String> resp = new ResponseEntity<>( only2CheckTimesAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEqualTo( "2019-04-04T08:31:20" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-04-04T08:31:20Z" );

		final String only1CheckTimeAvailable =
				"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":2019-01-04T15:30:50Z}]}";
		final ResponseEntity<String> resp2 = new ResponseEntity<>( only1CheckTimeAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp2 );

		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:30:50Z" );

		final String noCheckTimeAvailable =
				"{\"check_runs\":[{\"completed_at\":\"\"},{\"completed_at\":\"\"},{\"completed_at\":\"\"}]}";

		final ResponseEntity<String> resp3 = new ResponseEntity<>( noCheckTimeAvailable, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp3 );

		when( pullRequest.getLastUpdate() ).thenReturn( "2019-01-04T15:00:50Z" );
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotEmpty();
		assertThat( cut.newestChecksTime( pullRequest ) ).isNotNull();
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-01-04T15:00:50Z" );

	}

	@Test
	void getLatestUpdate_should_return_always_the_newest_time() {
		final PullRequest pullRequest = mock( PullRequest.class );
		final String repositoryTime1 = "{\"updated_at\": \"2019-02-04T15:00:53Z\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime1 );
		final String checksAreFinished1 =
				"{\"check_runs\":[{\"completed_at\":\"2019-02-04T15:00:50Z\"},{\"completed_at\":\"2019-02-04T15:00:55Z\"},{\"completed_at\":\"2019-02-04T15:00:35Z\"}]}";

		final ResponseEntity<String> resp = new ResponseEntity<>( checksAreFinished1, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-02-04T15:00:55Z" );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "2019-02-04T15:00:55Z" );

		final String repositoryTime2 = "{\"updated_at\": \"2019-02-04T15:00:59Z\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime2 );
		final String checksAreFinished2 =
				"{\"check_runs\":[{\"completed_at\":\"2019-02-04T15:00:50Z\"},{\"completed_at\":\"2019-02-04T15:00:55Z\"},{\"completed_at\":\"2019-02-04T15:00:35Z\"}]}";

		final ResponseEntity<String> resp2 = new ResponseEntity<>( checksAreFinished2, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp2 );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-02-04T15:00:55Z" );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "2019-02-04T15:00:59Z" );

		final String repositoryTime3 = "{\"updated_at\": \"2019-02-04T17:34:59Z\"}";
		when( template.getForObject( anyString(), eq( String.class ) ) ).thenReturn( repositoryTime3 );
		final String checksAreFinished3 =
				"{\"check_runs\":[{\"completed_at\":\"2019-02-04T07:35:50Z\"},{\"completed_at\":\"2019-02-04T15:23:12Z\"},{\"completed_at\":\"2019-02-04T19:23:30Z\"}]}";

		final ResponseEntity<String> resp3 = new ResponseEntity<>( checksAreFinished3, HttpStatus.OK );
		when( template.exchange( anyString(), any( HttpMethod.class ), any( HttpEntity.class ), eq( String.class ) ) )
				.thenReturn( resp3 );
		assertThat( cut.newestChecksTime( pullRequest ) ).isEqualTo( "2019-02-04T19:23:30Z" );
		assertThat( cut.getLatestUpdate( pullRequest ).getLastUpdate() ).isEqualTo( "2019-02-04T19:23:30Z" );

	}
}
