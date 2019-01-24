package org.retest.rebazer.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize( builder = BitbucketPullRequestResponse.BitbucketPullRequestResponseBuilder.class )
public class BitbucketPullRequestResponse {
	private String next;
}
