package org.retest.rebazer.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PullRequest {

	private final Integer id;
	private final String repo;
	private final String source;
	private final String destination;
	private final String url;
	private final String lastUpdate;

	@Override
	public String toString() {
		return "PR #" + id + " (" + source + " -> " + destination + ")";
	}
}
