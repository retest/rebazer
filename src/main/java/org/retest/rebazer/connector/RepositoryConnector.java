package org.retest.rebazer.connector;

import java.util.List;

import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.domain.PullRequest;

public interface RepositoryConnector {

	PullRequest getLatestUpdate( PullRequest pullRequest );

	boolean isApproved( PullRequest pullRequest );

	boolean rebaseNeeded( PullRequest pullRequest );

	void merge( PullRequest pullRequest );

	boolean greenBuildExists( PullRequest pullRequest );

	List<PullRequest> getAllPullRequests( RepositoryConfig repo );

	void addComment( PullRequest pullRequest );

}
