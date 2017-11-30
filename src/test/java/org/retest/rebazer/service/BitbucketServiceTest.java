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

	RestTemplate bitbucketTemplate;
	RestTemplate bitbucketLegacyTemplate;
	RebazerProperties config;
	RebaseService rebaseService;
	Map<Integer, String> pullrequestUpdateStates;

	BitbucketService cut;

	@Before
	public void setUp() {
		bitbucketTemplate = mock(RestTemplate.class);
		bitbucketLegacyTemplate = mock(RestTemplate.class);
		config = mock(RebazerProperties.class);
		rebaseService = mock(RebaseService.class);
		pullrequestUpdateStates = new HashMap<>();
		cut = new BitbucketService(bitbucketTemplate, bitbucketLegacyTemplate, config, rebaseService,
				pullrequestUpdateStates);
	}

	@Test
	public void isChangedSinceLastRun_should_return_false_if_pullrequest_didnt_change() {
		PullRequest pr = mock(PullRequest.class);
		when(pr.getId()).thenReturn(1);
		String timestamp = "2017-11-30T09:05:28+00:00";
		when(pr.getLastUpdate()).thenReturn(timestamp);
		pullrequestUpdateStates.put(1, timestamp);

		assertThat(cut.isChangedSinceLastRun(pr)).isFalse();
	}

	@Test
	public void isChangedSinceLastRun_should_return_true_if_pullrequest_changed() throws Exception {
		PullRequest pr = mock(PullRequest.class);
		when(pr.getId()).thenReturn(1);
		String timestampLastUpdate = "2017-11-30T10:05:28+00:00";
		when(pr.getLastUpdate()).thenReturn(timestampLastUpdate);
		String timestampUpdateStates = "2017-11-30T09:05:28+00:00";
		pullrequestUpdateStates.put(1, timestampUpdateStates);

		assertThat(cut.isChangedSinceLastRun(pr)).isTrue();
	}

}
