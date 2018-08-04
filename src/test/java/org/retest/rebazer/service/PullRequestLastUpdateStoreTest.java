package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;

public class PullRequestLastUpdateStoreTest {

	RepositoryConfig repoConfig;
	PullRequest pr;
	String lastUpdate;

	PullRequestLastUpdateStore cut;

	@Before
	public void setUp() {
		cut = new PullRequestLastUpdateStore();

		repoConfig = mock( RepositoryConfig.class );
		pr = mock( PullRequest.class );
		when( pr.getId() ).thenReturn( 1 );
		lastUpdate = "2017-11-30T10:05:28+00:00";
		when( pr.getLastUpdate() ).thenReturn( lastUpdate );
	}

	@Test
	public void isHandled_should_return_false_for_unknown_pr() {
		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	public void isHandled_should_return_true_if_pr_is_handled_before() {
		cut.setHandled( repoConfig, pr );
		assertThat( cut.isHandled( repoConfig, pr ) ).isTrue();
	}

	@Test
	public void isHandled_should_return_false_if_pr_is_handled_but_repo_is_reset_before() {
		cut.setHandled( repoConfig, pr );
		cut.resetAllInThisRepo( repoConfig );
		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	public void isHandled_should_return_false_if_pr_did_change() {
		cut.setHandled( repoConfig, pr );

		final String newDate = "2017-11-30T10:22:55+00:00";
		when( pr.getLastUpdate() ).thenReturn( newDate );

		assertThat( cut.isHandled( repoConfig, pr ) ).isFalse();
	}

	@Test
	public void getLastDate_should_return_null_for_unknown_pr() {
		assertThat( cut.getLastDate( repoConfig, pr ) ).isNull();
	}

	@Test
	public void getLastDate_should_return_the_date_if_pr_is_handled_before() {
		cut.setHandled( repoConfig, pr );
		assertThat( cut.getLastDate( repoConfig, pr ) ).isEqualTo( lastUpdate );
	}

	@Test
	@SuppressWarnings( "static-method" )
	public void computeIfAbsent_should_add_value_if_key_not_exist() {
		final HashMap<RepositoryConfig, Map<Integer, String>> multiMap = new HashMap<>();
		final RepositoryConfig key = mock( RepositoryConfig.class );

		final Map<Integer, String> mapForKey = multiMap.computeIfAbsent( key, k -> new HashMap<>() );

		assertThat( mapForKey ).isNotNull();
		assertThat( multiMap.get( key ) ).isSameAs( mapForKey );
		assertThat( multiMap.computeIfAbsent( key, k -> new HashMap<>() ) ).isSameAs( mapForKey );
	}

}
