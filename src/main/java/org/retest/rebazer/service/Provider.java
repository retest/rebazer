package org.retest.rebazer.service;

import java.util.List;

import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.web.client.RestTemplate;

public interface Provider {

	PullRequest getLatestUpdate( PullRequest pullRequest, RestTemplate template );

	boolean isApproved( PullRequest pullRequest, RestTemplate template );

	boolean rebaseNeeded( PullRequest pullRequest, Team team, RestTemplate template );

	String getHeadOfBranch( PullRequest pullRequest, Team team, RestTemplate template );

	String getLastCommonCommitId( PullRequest pullRequest, RestTemplate template );

	void merge( PullRequest pullRequest, RestTemplate template );

	boolean greenBuildExists( PullRequest pullRequest, Team team, RestTemplate template );

	List<PullRequest> getAllPullRequests( Repository repo, Team team, RestTemplate template );

	void addComment( PullRequest pullRequest, Team team, RestTemplate template );

	void rebase( Repository repo, PullRequest pullRequest, Team team, RestTemplate template );

}
