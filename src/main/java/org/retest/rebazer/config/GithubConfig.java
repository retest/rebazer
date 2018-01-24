package org.retest.rebazer.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

public class GithubConfig {

	private final static String baseUrl = "https://api.github.com/";

	@Bean
	public RestTemplate githubTemplate( final RestTemplateBuilder builder, final String user, final String pass ) {
		return builder.basicAuthorization( user, pass ).rootUri( baseUrl ).build();
	}

}
