package org.retest.rebazer.service;

import java.util.HashMap;
import java.util.Map;

import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.stereotype.Service;

@Service
public class PullRequestLastUpdateStore {

	private final Map<RepositoryConfig, Map<Integer, String>> pullRequestUpdateStates = new HashMap<>();

	public void setHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		getMapFor( repoConfig ).put( pullRequest.getId(), pullRequest.getLastUpdate() );
	}

	public String getLastDate( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		return getMapFor( repoConfig ).get( pullRequest.getId() );
	}

	public void resetAllInThisRepo( final RepositoryConfig repoConfig ) {
		pullRequestUpdateStates.remove( repoConfig );
	}

	public boolean isHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		return pullRequest.getLastUpdate().equals( getLastDate( repoConfig, pullRequest ) );
	}

	private Map<Integer, String> getMapFor( final RepositoryConfig repoConfig ) {
		return pullRequestUpdateStates.computeIfAbsent( repoConfig, key -> new HashMap<>() );
	}

}
