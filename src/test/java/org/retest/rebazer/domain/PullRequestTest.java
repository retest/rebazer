package org.retest.rebazer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Date;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PullRequestTest {

	PullRequest pullRequest;

	@BeforeEach
	void setUp() {
		final Date lastUpdate = Date.from( OffsetDateTime.parse( "2017-11-30T10:05:28Z" ).toInstant() );
		pullRequest = new PullRequest( 1, "title", 2, "description", Maps.newHashMap( 1, "CHANGES_REQUESTED" ),
				"source", "destination", lastUpdate );
	}

	@Test
	void toString_should_return_correct_string() {
		assertThat( pullRequest ).hasToString( "PR #1 (source -> destination)" );
	}

	@Test
	void mergeCommitMessage_should_return_corrct_string() {
		assertThat( pullRequest.mergeCommitMessage() ).isEqualTo( "Merged in source (pull request #1) by rebazer" );
	}
}
