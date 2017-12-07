package org.retest.rebazer.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BitbucketService {

	@Autowired
	private RestTemplate bitbucketTemplate;

	@Autowired
	private RestTemplate bitbucketLegacyTemplate;

	@Autowired
	private RebazerConfig config;

	private RebaseService rebaseService;

	private Map<Integer, String> pullRequestUpdateStates = new HashMap<>();

	public BitbucketService(RebaseService rebaseService) {
		this.rebaseService = rebaseService;
	}
	
    /**
     * Testing only.
     */
    BitbucketService(RestTemplate bitbucketTemplate, RestTemplate bitbucketLegacyTemplate, RebazerConfig config, RebaseService rebaseService, Map<Integer, String> pullRequestUpdateStates) {
        this.bitbucketTemplate = bitbucketTemplate;
        this.bitbucketLegacyTemplate = bitbucketLegacyTemplate;
        this.config = config;
        this.rebaseService = rebaseService;
        this.pullRequestUpdateStates = pullRequestUpdateStates;
    }

	@Scheduled(fixedDelay = 10 * 1000)
	public void pollBitbucket() {
		for (Repository repo : config.getRepos()) {
			log.info("Processing {}.", repo);
			for (PullRequest pr : getAllPullRequests(repo)) {
				handlePR(repo, pr);
			}
		}
	}

	private void handlePR(Repository repo, PullRequest pullRequest) {
		log.info("Processing {}.", pullRequest);
		
		if (!hasChangedSinceLastRun(pullRequest)) {
			log.info("{} is unchanged since last run (last change: {}).", pullRequest,
					pullRequestUpdateStates.get(pullRequest.getId()));
			return;
		}
		
		pullRequestUpdateStates.put(pullRequest.getId(), pullRequest.getLastUpdate());
		
		if (!greenBuildExists(pullRequest)) {
			log.info("Waiting for green build of {}.", pullRequest);
		} else if (!isApproved(pullRequest)) {
			log.info("Waiting for approval of {}.", pullRequest);
		} else if (rebaseNeeded(pullRequest)) {
			rebaseService.rebase(repo, pullRequest);
		} else {
			merge(pullRequest);
			pullRequestUpdateStates.remove(pullRequest.getId());
		}
	}

	boolean hasChangedSinceLastRun(PullRequest pullRequest) {
		return !pullRequest.getLastUpdate().equals(pullRequestUpdateStates.get(pullRequest.getId()));
	}

	private boolean isApproved(PullRequest pullRequest) {
		final DocumentContext jp = jsonPathForPath(pullRequest.getUrl());
		return jp.<List<Boolean>>read("$.participants[*].approved").stream().anyMatch(approved -> approved);
	}

	private boolean rebaseNeeded(PullRequest pullRequest) {
		return !getLastCommonCommitId(pullRequest).equals(getHeadOfBranch(pullRequest));
	}

	private String getHeadOfBranch(PullRequest pullRequest) {
		String url = "/repositories/" + config.getTeam() + "/" + pullRequest.getRepo() + "/";
		return jsonPathForPath(url + "refs/branches/" + pullRequest.getDestination()).read("$.target.hash");
	}

	private String getLastCommonCommitId(PullRequest pullRequest) {
		DocumentContext jp = jsonPathForPath(pullRequest.getUrl() + "/commits");

		final int pageLength = jp.read("$.pagelen");
		final int size = jp.read("$.size");
		final int lastPage = (pageLength + size - 1) / pageLength;

		if (lastPage > 1) {
			jp = jsonPathForPath(pullRequest.getUrl() + "/commits?page=" + lastPage);
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

		bitbucketTemplate.postForObject(pullRequest.getUrl() + "/merge", request, Object.class);
	}

	private boolean greenBuildExists(PullRequest pullRequest) {
		final DocumentContext jp = jsonPathForPath(pullRequest.getUrl() + "/statuses");
		return jp.<List<String>>read("$.values[*].state").stream().anyMatch(s -> s.equals("SUCCESSFUL"));
	}

	private List<PullRequest> getAllPullRequests(Repository repo) {
		final String urlPath = "/repositories/" + config.getTeam() + "/" + repo.getName() + "/pullrequests";
		final DocumentContext jp = jsonPathForPath(urlPath);
		return parsePullRequestsJson(repo, urlPath, jp);
	}

	private static List<PullRequest> parsePullRequestsJson(Repository repo, final String urlPath, final DocumentContext jp) {
		int numPullRequests = (int) jp.read("$.size");
		final List<PullRequest> results = new ArrayList<>(numPullRequests);
		for (int i = 0; i < numPullRequests; i++) {
			final int id = jp.read("$.values[" + i + "].id");
			final String source = jp.read("$.values[" + i + "].source.branch.name");
			final String destination = jp.read("$.values[" + i + "].destination.branch.name");
			final String lastUpdate = jp.read("$.values[" + i + "].updated_on");
			results.add(PullRequest.builder() //
					.id(id) //
					.repo(repo.getName()) //
					.source(source) //
					.destination(destination) //
					.url(urlPath + "/" + id) //
					.lastUpdate(lastUpdate) //
					.build()); //
		}
		return results;
	}

	private DocumentContext jsonPathForPath(String urlPath) {
		final String json = bitbucketTemplate.getForObject(urlPath, String.class);
		return JsonPath.parse(json);
	}

	private void addComment(PullRequest pullRequest) {
		Map<String, String> request = new HashMap<>();
		request.put("content", "This pull request needs some manual love ...");
		bitbucketLegacyTemplate.postForObject(pullRequest.getUrl() + "/comments", request, String.class);
	}

}
