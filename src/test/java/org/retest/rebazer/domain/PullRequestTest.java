package org.retest.rebazer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PullRequestTest {

	PullRequest pullRequest;

	@BeforeEach
	void setUp() {
		final Date lastUpdate = Date.from( OffsetDateTime.parse( "2017-11-30T10:05:28Z" ).toInstant() );
		final Map<Integer, String> reviewers = Maps.newHashMap( 1, "CHANGES_REQUESTED" );
		pullRequest = PullRequest.builder() //
				.id( 1 ).title( "title" ).description( "description" ) //
				.creator( 2 ).reviewers( reviewers ) //
				.source( "source" ).destination( "destination" ) //
				.lastUpdate( lastUpdate ) //
				.build();
	}

	@Test
	void toString_should_return_correct_string() {
		assertThat( pullRequest ).hasToString( "PR #1 (source -> destination)" );
	}

	@Test
	void mergeCommitMessage_should_return_corrct_string() {
		assertThat( pullRequest.mergeCommitMessage() ).isEqualTo( "Merged in source (pull request #1) by rebazer" );
	}

	@ParameterizedTest
	@MethodSource( "allReviewersRequestedTypes" )
	void isReviewByAllReviewersRequested_should_handle_all_variants_correct( final String title,
			final String description, final boolean result ) {

		pullRequest = PullRequest.builder().title( title ).description( description ).build();

		assertThat( pullRequest.isReviewByAllReviewersRequested() ).isEqualTo( result );
	}

	private static Stream<Arguments> allReviewersRequestedTypes() {
		return Stream.of( //
				Arguments.of( "new_feature", "Please pull these awesome changes", false ),
				Arguments.of( "new_feature", "Please pull these awesome changes @All", true ),
				Arguments.of( "new_feature @All", "Please pull these awesome changes", true ),
				Arguments.of( "new_feature @All", "Please pull these awesome changes @All", true ) );
	}

}
