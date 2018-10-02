package org.retest.rebazer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PullRequestTest {

	private PullRequest pullRequest;

	@BeforeAll
	void setUp() {
		pullRequest = new PullRequest( 1, "source", "destination", "lastUpdate" );
	}

	@Test
	void toString_should_return_correct_string() {
		assertThat( pullRequest ).hasToString( "PR #1 (source -> destination)" );
	}

	@Test
	void mergeCommitMessage_should_return_corrct_string() {
		assertThat( pullRequest.mergeCommitMessage() ).isEqualTo( "Merged in source (pull request #1) by ReBaZer" );
	}
}
