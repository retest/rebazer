package org.retest.rebazer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.retest.rebazer.config.RebazerProperties;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.web.client.RestTemplate;

public class BitbucketServiceTest {

	Map<Integer, String> pullRequestUpdateStates;

	BitbucketService cut;

	@Before
	public void setUp() {
		RestTemplate bitbucketTemplate = mock(RestTemplate.class);
		RestTemplate bitbucketLegacyTemplate = mock(RestTemplate.class);
		RebazerProperties config = mock(RebazerProperties.class);
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

}
