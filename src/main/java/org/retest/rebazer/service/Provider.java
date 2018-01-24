package org.retest.rebazer.service;

import java.util.List;

import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.config.RebazerConfig.Team;
import org.retest.rebazer.domain.PullRequest;

public interface Provider {

	PullRequest getLatestUpdate( PullRequest pullRequest );

	boolean isApproved( PullRequest pullRequest );

	boolean rebaseNeeded( PullRequest pullRequest, Team team );

	String getHeadOfBranch( PullRequest pullRequest, Team team );

	String getLastCommonCommitId( PullRequest pullRequest );

	void merge( PullRequest pullRequest );

	boolean greenBuildExists( PullRequest pullRequest, Team team );

	List<PullRequest> getAllPullRequests( Repository repo, Team team );

	void rebase( Repository repo, PullRequest pullRequest, Team team );

	void addComment( PullRequest pullRequest, Team team );

}
