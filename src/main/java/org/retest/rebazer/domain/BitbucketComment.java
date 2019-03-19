package org.retest.rebazer.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BitbucketComment {

	private BitbucketContent content;

}
