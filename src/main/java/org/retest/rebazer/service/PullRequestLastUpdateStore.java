package org.retest.rebazer.service;

import java.util.HashMap;
import java.util.Map;

import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.stereotype.Service;

@Service
public class PullRequestLastUpdateStore {

	private final Map<RepositoryConfig, Map<Integer, String>> pullRequestUpdateStates = new HashMap<>();

	public void setHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		Map<Integer, String> repoMap = pullRequestUpdateStates.get( repoConfig );
		if ( repoMap == null ) {
			repoMap = new HashMap<>();
			pullRequestUpdateStates.put( repoConfig, repoMap );
		}
		repoMap.put( pullRequest.getId(), pullRequest.getLastUpdate() );
	}

	public String getLastDate( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		final Map<Integer, String> repoMap = pullRequestUpdateStates.get( repoConfig );
		return repoMap != null ? repoMap.get( pullRequest.getId() ) : null;
	}

	public void resetAllInThisRepo( final RepositoryConfig repoConfig ) {
		final Map<Integer, String> repoMap = pullRequestUpdateStates.get( repoConfig );
		if ( repoMap != null ) {
			repoMap.clear();
		}
	}

	public boolean isHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		return pullRequest.getLastUpdate().equals( getLastDate( repoConfig, pullRequest ) );
	}

}
