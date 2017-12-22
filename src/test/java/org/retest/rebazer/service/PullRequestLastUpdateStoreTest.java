package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;

public class PullRequestLastUpdateStoreTest {

	Repository repo;
	PullRequest pr;
	String lastUpdate;

	PullRequestLastUpdateStore cut;

	@Before
	public void setUp() {
		cut = new PullRequestLastUpdateStore();

		repo = mock( Repository.class );
		pr = mock( PullRequest.class );
		when( pr.getId() ).thenReturn( 1 );
		lastUpdate = "2017-11-30T10:05:28+00:00";
		when( pr.getLastUpdate() ).thenReturn( lastUpdate );
	}

	@Test
	public void isHandled_should_return_false_for_unknown_pr() {
		assertThat( cut.isHandled( repo, pr ) ).isFalse();
	}

	@Test
	public void isHandled_should_return_true_if_pr_is_handled_before() {
		cut.setHandled( repo, pr );
		assertThat( cut.isHandled( repo, pr ) ).isTrue();
	}

	@Test
	public void isHandled_should_return_false_if_pr_is_handled_but_repo_is_reset_before() {
		cut.setHandled( repo, pr );
		cut.resetAllInThisRepo( repo );
		assertThat( cut.isHandled( repo, pr ) ).isFalse();
	}

	@Test
	public void isHandled_should_return_false_if_pr_did_change() throws Exception {
		cut.setHandled( repo, pr );

		final String newDate = "2017-11-30T10:22:55+00:00";
		when( pr.getLastUpdate() ).thenReturn( newDate );

		assertThat( cut.isHandled( repo, pr ) ).isFalse();
	}

	@Test
	public void getLastDate_should_return_null_for_unknown_pr() {
		assertThat( cut.getLastDate( repo, pr ) ).isNull();
	}

	@Test
	public void getLastDate_should_return_the_date_if_pr_is_handled_before() {
		cut.setHandled( repo, pr );
		assertThat( cut.getLastDate( repo, pr ) ).isEqualTo( lastUpdate );
	}

}
