package org.retest.rebazer.connector;

import java.util.List;

import org.retest.rebazer.domain.PullRequest;

public interface RepositoryConnector {

	List<PullRequest> getAllPullRequests();

	PullRequest getLatestUpdate( PullRequest pullRequest );

	boolean isApproved( PullRequest pullRequest );

	boolean rebaseNeeded( PullRequest pullRequest );

	boolean greenBuildExists( PullRequest pullRequest );

	void merge( PullRequest pullRequest );

	void addComment( PullRequest pullRequest );

}
