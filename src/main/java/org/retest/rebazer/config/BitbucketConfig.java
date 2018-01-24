package org.retest.rebazer.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

public class BitbucketConfig {

	private final static String baseUrlV1 = "https://api.bitbucket.org/1.0";
	private final static String baseUrlV2 = "https://api.bitbucket.org/2.0";

	@Bean
	public RestTemplate bitbucketLegacyTemplate( final RestTemplateBuilder builder, final String user,
			final String pass ) {
		return builder.basicAuthorization( user, pass ).rootUri( baseUrlV1 ).build();
	}

	@Bean
	public RestTemplate bitbucketTemplate( final RestTemplateBuilder builder, final String user, final String pass ) {
		return builder.basicAuthorization( user, pass ).rootUri( baseUrlV2 ).build();
	}

}
