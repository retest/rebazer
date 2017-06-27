package org.retest.rebazer.domain;

import lombok.Data;

@Data
public class PullRequest {

	private final Integer id;
	private final String source;
	private final String destination;

	@Override
	public String toString() {
		return "PR" + id + " ( " + source + " -> " + destination + " )";
	}
}