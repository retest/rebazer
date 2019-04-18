package org.retest.rebazer.service;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.retest.rebazer.domain.PullRequest;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.stereotype.Service;

@Service
public class PullRequestLastUpdateStore {

	private final Map<RepositoryConfig, Map<Integer, Date>> pullRequestUpdateStates = new HashMap<>();
	protected static final Date FALLBACK_REPOSITORY_TIME = parseStringToDate( "2019-01-01T00:00:00Z" );

	public static Date parseStringToDate( final String date ) {
		return Date.from( OffsetDateTime.parse( date ).toInstant() );
	}

	public void setHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		getMapFor( repoConfig ).put( pullRequest.getId(), pullRequest.getLastUpdate() );
	}

	public Date getLastDate( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		return getMapFor( repoConfig ).getOrDefault( pullRequest.getId(), FALLBACK_REPOSITORY_TIME );
	}

	public void resetAllInThisRepo( final RepositoryConfig repoConfig ) {
		pullRequestUpdateStates.remove( repoConfig );
	}

	public boolean isHandled( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		return pullRequest.getLastUpdate().compareTo( getLastDate( repoConfig, pullRequest ) ) == 0;
	}

	private Map<Integer, Date> getMapFor( final RepositoryConfig repoConfig ) {
		return pullRequestUpdateStates.computeIfAbsent( repoConfig, key -> new HashMap<>() );
	}

}
