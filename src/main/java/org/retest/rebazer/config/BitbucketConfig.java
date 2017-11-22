package org.retest.rebazer.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class BitbucketConfig {

	private final static String baseUrlV1 = "https://api.bitbucket.org/1.0";
	private final static String baseUrlV2 = "https://api.bitbucket.org/2.0";
	
	@Autowired
	private RebazerProperties properties;

	@Bean
	public RestTemplate bitbucketLegacyTemplate(RestTemplateBuilder builder) {
		return builder.basicAuthorization(properties.getUser(), properties.getPass())
				.rootUri(baseUrlV1).build();
	}

	@Bean
	public RestTemplate bitbucketTemplate(RestTemplateBuilder builder) {
		return builder.basicAuthorization(properties.getUser(), properties.getPass())
				.rootUri(baseUrlV2).build();
	}

}