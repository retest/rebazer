package org.retest.rebazer.service;

import java.util.ArrayList;
import java.util.List;

import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestOperations;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@Service
public class BitbucketService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BitbucketService.class);

	@Autowired
	RestOperations restOperations;

	@Autowired
	RebaseService rebaseService;

	@Value("${rebazer.repo.url}")
	String baseUrl;

	@Scheduled(fixedDelay = 5000)
	public void pollBitbucket() {
		final List<PullRequest> allPullRequests = getAllPullRequestIds();

		for (final PullRequest pullRequest : allPullRequests) {
			logger.debug("processing " + pullRequest);

			if (!greenBuildExists(pullRequest)) {
				logger.info("waiting for green builds " + pullRequest);
			} else if (rebaseNeeded(pullRequest)) {
				rebaseService.rebase(pullRequest);
			} else if (!isApproved(pullRequest)) {
				logger.warn("approval required " + pullRequest);
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
		final DocumentContext jp = jsonPathForPath("pullrequests/" + pullRequest.getId() + "/commits");
		final List<String> commitIds = jp.read("$.values[*].hash");
		final List<String> parentIds = jp.read("$.values[*].parents[0].hash");

		return parentIds.stream().filter(parent -> !commitIds.contains(parent)).findFirst()
				.orElseThrow(IllegalStateException::new);
	}

	private void merge(PullRequest pullRequest) {
		logger.warn("merge " + pullRequest);
		restOperations.postForObject(baseUrl + "/pullrequests/" + pullRequest.getId() + "/merge", null, Object.class);
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
		final String json = restOperations.getForObject(baseUrl + "/" + string, String.class);
		return JsonPath.parse(json);
	}

}
