package org.retest.rebazer.service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.retest.rebazer.config.RebazerProperties;
import org.retest.rebazer.config.RebazerProperties.Repository;
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
	private RebazerProperties config;

	private RebaseService rebaseService;

	private Map<Integer, Date> doNotRebase = new HashMap<Integer, Date>();

	public BitbucketService(RebaseService rebaseService) {
		this.rebaseService = rebaseService;
	}

	@Scheduled(fixedDelay = 60 * 1000)
	public void pollBitbucket() {

		config.getRepos().forEach(repo -> {
			log.info("Processing repository: {}", repo.getName());
			getAllPullRequestIds(repo).forEach(pullRequest -> {
				log.info("Processing " + pullRequest);
				if (!greenBuildExists(pullRequest)) {
					log.info("Waiting for green builds on " + pullRequest);
				} else if (rebaseNeeded(pullRequest)) {
					Iterator<Map.Entry<Integer, Date>> entries = doNotRebase.entrySet().iterator();
					while (entries.hasNext() || doNotRebase.isEmpty()) {
						if (doNotRebase.isEmpty()) {
							log.info("Waiting for rebase on " + pullRequest);
							rebaseService.rebase(repo, pullRequest);
							doNotRebase.put(pullRequest.getId(), pullRequest.getLastUpdate());
						} else {
							Map.Entry<Integer, Date> entry = entries.next();
							if ((!entry.getKey().equals(pullRequest.getId()))
									&& (!entry.getValue().equals(pullRequest.getLastUpdate()))) {
								log.info("Waiting for rebase on " + pullRequest);
								rebaseService.rebase(repo, pullRequest);
								doNotRebase.put(pullRequest.getId(), pullRequest.getLastUpdate());
							}
						}
					}
				} else if (!isApproved(pullRequest)) {
					log.warn("Waiting for approval on " + pullRequest);
				} else {
					merge(pullRequest);
				}
			});
		});
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

	private List<PullRequest> getAllPullRequestIds(Repository repo) {
		final String urlPath = "/repositories/" + config.getTeam() + "/" + repo.getName() + "/pullrequests";

		final DocumentContext jp = jsonPathForPath(urlPath);
		final List<PullRequest> results = new ArrayList<>();

		for (Integer i = 0; i < (int) jp.read("$.size"); i++) {
			final Integer id = jp.read("$.values[" + i + "].id");
			final String source = jp.read("$.values[" + i + "].source.branch.name");
			final String destination = jp.read("$.values[" + i + "].destination.branch.name");
			final Date lastUpdate = parseDate(jp.read("$.values[" + i + "].updated_on"));
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

	private Date parseDate(String targetDate) {
		SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'+'HH:mm");
		Date parsedDate;
		try {
			parsedDate = dateFormatter.parse(targetDate);
			System.out.println(parsedDate);
			return parsedDate;
		} catch (Exception e) {
			log.info("Date could not be parsed " + e.getMessage());
		}
		return null;
	}

}
