package org.retest.rebazer.service;

import java.util.List;

import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;

public interface Provider {

	PullRequest getLatestUpdate( PullRequest pullRequest );

	boolean isApproved( PullRequest pullRequest );

	boolean rebaseNeeded( PullRequest pullRequest );

	String getHeadOfBranch( PullRequest pullRequest );

	String getLastCommonCommitId( PullRequest pullRequest );

	void merge( PullRequest pullRequest );

	boolean greenBuildExists( PullRequest pullRequest );

	List<PullRequest> getAllPullRequests( Repository repo );

	void rebase( Repository repo, PullRequest pullRequest );

	void addComment( PullRequest pullRequest );

}
