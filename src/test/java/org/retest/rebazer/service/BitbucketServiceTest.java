package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.web.client.RestTemplate;

public class BitbucketServiceTest {

	Map<Integer, String> pullRequestUpdateStates;
	RestTemplate bitbucketTemplate;

	BitbucketService cut;

	@Before
	public void setUp() {
		bitbucketTemplate = mock(RestTemplate.class);
		RestTemplate bitbucketLegacyTemplate = mock(RestTemplate.class);
		RebazerConfig config = mock(RebazerConfig.class);
		RebaseService rebaseService = mock(RebaseService.class);
		pullRequestUpdateStates = new HashMap<>();
		cut = new BitbucketService(bitbucketTemplate, bitbucketLegacyTemplate, config, rebaseService,
				pullRequestUpdateStates);
	}

	@Test
	public void hasChangedSinceLastRun_should_return_false_if_pullrequest_didnt_change() {
		PullRequest pr = mock(PullRequest.class);
		when(pr.getId()).thenReturn(1);
		String timestamp = "2017-11-30T09:05:28+00:00";
		when(pr.getLastUpdate()).thenReturn(timestamp);
		pullRequestUpdateStates.put(1, timestamp);

		assertThat(cut.hasChangedSinceLastRun(pr)).isFalse();
	}

	@Test
	public void hasChangedSinceLastRun_should_return_true_if_pullrequest_did_change() throws Exception {
		PullRequest pr = mock(PullRequest.class);
		when(pr.getId()).thenReturn(1);
		String timestampLastUpdate = "2017-11-30T10:05:28+00:00";
		when(pr.getLastUpdate()).thenReturn(timestampLastUpdate);
		String timestampUpdateStates = "2017-11-30T09:05:28+00:00";
		pullRequestUpdateStates.put(1, timestampUpdateStates);

		assertThat(cut.hasChangedSinceLastRun(pr)).isTrue();
	}

	@Test
	public void rebaseNeeded_should_return_false_if_headOfBranch_is_equal_to_lastCommonCommitId() {
		PullRequest pullRequest = mock(PullRequest.class);
		BitbucketService cut = mock(BitbucketService.class);
		String head = "12325345923759135";
		when(cut.getHeadOfBranch(pullRequest)).thenReturn(head);
		when(cut.getLastCommonCommitId(pullRequest)).thenReturn(head);
		when(cut.rebaseNeeded(pullRequest)).thenCallRealMethod();

		assertThat(cut.rebaseNeeded(pullRequest)).isFalse();
	}

	@Test
	public void rebaseNeeded_should_return_true_if_headOfBranch_isnt_equal_to_lastCommonCommitId() {
		PullRequest pullRequest = mock(PullRequest.class);
		BitbucketService cut = mock(BitbucketService.class);
		String head = "12325345923759135";
		String lcci = "21342343253253452";
		when(cut.getHeadOfBranch(pullRequest)).thenReturn(head);
		when(cut.getLastCommonCommitId(pullRequest)).thenReturn(lcci);
		when(cut.rebaseNeeded(pullRequest)).thenCallRealMethod();

		assertThat(cut.rebaseNeeded(pullRequest)).isTrue();
	}

	@Test
	public void isApproved_should_return_false_if_approved_is_false() {
		PullRequest pullRequest = mock(PullRequest.class);
		String json = "{participants: [{\"approved\": false}]}\"";
		when(bitbucketTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

		assertThat(cut.isApproved(pullRequest)).isFalse();
	}

	@Test
	public void isApproved_should_return_ture_if_approved_is_true() {
		PullRequest pullRequest = mock(PullRequest.class);
		String json = "{participants: [{\"approved\": true}]}\"";
		when(bitbucketTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

		assertThat(cut.isApproved(pullRequest)).isTrue();
	}

	@Test
	public void greenBuildExists_should_return_false_if_state_is_failed() {
		PullRequest pullRequest = mock(PullRequest.class);
		String json = "{values: [{\"state\": FAILED}]}";
		when(bitbucketTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

		assertThat(cut.greenBuildExists(pullRequest)).isFalse();
	}

	@Test
	public void greenBuildExists_should_return_true_if_state_is_successful() {
		PullRequest pullRequest = mock(PullRequest.class);
		String json = "{values: [{\"state\": SUCCESSFUL}]}";
		when(bitbucketTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

		assertThat(cut.greenBuildExists(pullRequest)).isTrue();
	}

}
