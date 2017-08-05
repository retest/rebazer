package org.retest.rebazer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.config.ReBaZerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BitbucketService {

	@Autowired
	RestOperations restOperations;

	@Autowired
	ReBaZerConfig config;

	@Autowired
	RebaseService rebaseService;

	@Scheduled(fixedDelay = 60 * 1000)
	public void pollBitbucket() {
		final List<PullRequest> allPullRequests = getAllPullRequestIds();

		for (final PullRequest pullRequest : allPullRequests) {
			log.debug("processing " + pullRequest);

			if (!greenBuildExists(pullRequest)) {
				log.info("waiting for green builds " + pullRequest);
			} else if (rebaseNeeded(pullRequest)) {
				rebaseService.rebase(pullRequest);
			} else if (!isApproved(pullRequest)) {
				log.warn("approval required " + pullRequest);
			} else {
				merge(pullRequest);
			}
		}
	}

	private boolean isApproved(PullRequest pullRequest) {
		final DocumentContext jp = jsonPathForPath("pullrequests/" + pullRequest.getId());
		return jp.<List<Boolean>>read("$.participants[*].approved").stream().anyMatch(approved -> approved);
	}

	private boolean rebaseNeeded(PullRequest pullRequest) {
		return !getLastCommonCommitId(pullRequest).equals(getHeadOfBranch(pullRequest.getDestination()));
	}

	private String getHeadOfBranch(String branch) {
		return jsonPathForPath("refs/branches/" + branch).read("$.target.hash");
	}

	private String getLastCommonCommitId(PullRequest pullRequest) {
		DocumentContext jp = jsonPathForPath("pullrequests/" + pullRequest.getId() + "/commits");

		final int pageLength = jp.read("$.pagelen");
		final int size = jp.read("$.size");
		final int lastPage = (pageLength + size - 1) / pageLength;

		if (lastPage > 1) {
			jp = jsonPathForPath("pullrequests/" + pullRequest.getId() + "/commits?page=" + lastPage);
		}

		final List<String> commitIds = jp.read("$.values[*].hash");
		final List<String> parentIds = jp.read("$.values[*].parents[0].hash");

		return parentIds.stream().filter(parent -> !commitIds.contains(parent)).findFirst()
				.orElseThrow(IllegalStateException::new);
	}

	private void merge(PullRequest pullRequest) {
		log.warn("Merging pull request " + pullRequest);
		String message = String.format("Merged in %s (pull request #%d) by ReBaZer", pullRequest.getSource(),
				pullRequest.getId());
		Map<String, Object> request = new HashMap<>();
		request.put("close_source_branch", true);
		request.put("message", message);
		request.put("merge_strategy", "merge_commit");

		restOperations.postForObject(config.getApiBaseUrl() + "/pullrequests/" + pullRequest.getId() + "/merge",
				request, Object.class);
	}

	private boolean greenBuildExists(PullRequest pullRequest) {
		final DocumentContext jp = jsonPathForPath("pullrequests/" + pullRequest.getId() + "/statuses");
		return jp.<List<String>>read("$.values[*].state").stream().anyMatch(s -> s.equals("SUCCESSFUL"));
	}

	private List<PullRequest> getAllPullRequestIds() {
		final DocumentContext jp = jsonPathForPath("pullrequests");
		final List<PullRequest> results = new ArrayList<>();

		for (Integer i = 0; i < (int) jp.read("$.size"); i++) {
			final Integer id = jp.read("$.values[" + i + "].id");
			final String source = jp.read("$.values[" + i + "].source.branch.name");
			final String destination = jp.read("$.values[" + i + "].destination.branch.name");
			results.add(new PullRequest(id, source, destination));
		}
		return results;
	}

	private DocumentContext jsonPathForPath(String string) {
		final String json = restOperations.getForObject(config.getApiBaseUrl() + "/" + string, String.class);
		return JsonPath.parse(json);
	}

}
