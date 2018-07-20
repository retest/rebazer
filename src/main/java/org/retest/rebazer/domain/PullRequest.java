package org.retest.rebazer.domain;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Builder
public class PullRequest {

	private final Integer id;
	private final String source;
	private final String destination;
	@EqualsAndHashCode.Exclude
	private final String lastUpdate;

	@Override
	public String toString() {
		return "PR #" + id + " (" + source + " -> " + destination + ")";
	}
}
