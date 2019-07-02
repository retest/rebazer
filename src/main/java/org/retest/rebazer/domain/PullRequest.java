package org.retest.rebazer.domain;

import java.util.Date;
import java.util.Map;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
public class PullRequest {

	private final Integer id;
	private final String title;
	private final Integer creator;
	private final String description;
	private final Map<Integer, String> reviewers;
	private final String source;
	private final String destination;
	@EqualsAndHashCode.Exclude
	private final Date lastUpdate;

	@Override
	public String toString() {
		return "PR #" + id + " (" + source + " -> " + destination + ")";
	}

	public String mergeCommitMessage() {
		return String.format( "Merged in %s (pull request #%d) by rebazer", source, id );
	}

}
