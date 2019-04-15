package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;

class PullRequestLastUpdateStoreTest {

	RepositoryConfig repoConfig;
	PullRequest pr;

	PullRequestLastUpdateStore cut;

	@BeforeEach
	void setUp() {
		cut = new PullRequestLastUpdateStore();

		repoConfig = mock( RepositoryConfig.class );
		pr = mock( PullRequest.class );
		when( pr.getId() ).thenReturn( 1 );
		final Date lastUpdate = Date.from( OffsetDateTime.parse( "2017-11-30T10:05:28Z" ).toInstant() );
		when( pr.getLastUpdate() ).thenReturn( lastUpdate );
	}

	@Test
	void isHandled_should_return_false_for_unknown_pr() {
		when( cut.getLastDate( repoConfig, pr ) ).thenReturn( null );
		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	void isHandled_should_return_true_if_pr_is_handled_before() {
		cut.setHandled( repoConfig, pr );
		assertThat( cut.isHandled( repoConfig, pr ) ).isTrue();
	}

	@Test
	void isHandled_should_return_false_if_pr_is_handled_but_repo_is_reset_before() {
		cut.setHandled( repoConfig, pr );
		cut.resetAllInThisRepo( repoConfig );
		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	void isHandled_should_return_false_if_pr_did_change() {
		cut.setHandled( repoConfig, pr );

		final Date newDate = Date.from( OffsetDateTime.parse( "2017-11-30T10:22:55+00:00" ).toInstant() );
		when( pr.getLastUpdate() ).thenReturn( newDate );
		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	void getLastDate_should_return_dummy_value_for_unknown_pr() {
		assertThat( cut.getLastDate( repoConfig, pr ) ).isEqualTo( "2019-01-01T00:00:00Z" );
	}

	@Test
	void getLastDate_should_return_the_date_if_pr_is_handled_before() {
		cut.setHandled( repoConfig, pr );
		assertThat( cut.getLastDate( repoConfig, pr ) ).isEqualTo( "2017-11-30T10:05:28Z" );

	}

	@Test
	@SuppressWarnings( "static-method" )
	void computeIfAbsent_should_add_value_if_key_not_exist() {
		final HashMap<RepositoryConfig, Map<Integer, Date>> multiMap = new HashMap<>();
		final RepositoryConfig key = mock( RepositoryConfig.class );

		final Map<Integer, Date> mapForKey = multiMap.computeIfAbsent( key, k -> new HashMap<>() );

		assertThat( mapForKey ).isNotNull();
		assertThat( multiMap.get( key ) ).isSameAs( mapForKey );
		assertThat( multiMap.computeIfAbsent( key, k -> new HashMap<>() ) ).isSameAs( mapForKey );
	}

}
