package org.retest.rebazer.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor( access = AccessLevel.PRIVATE )
@JsonDeserialize( builder = BitbucketPullRequestResponse.BitbucketPullRequestResponseBuilder.class )
public class BitbucketPullRequestResponse {
	private String next;
}
