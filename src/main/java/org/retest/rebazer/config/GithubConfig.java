package org.retest.rebazer.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class GithubConfig {

	private final static String baseUrl = "https://api.github.com/";

	@Autowired
	private RebazerConfig config;

	@Bean
	public RestTemplate githubTemplate( final RestTemplateBuilder builder ) {
		return builder.basicAuthorization( config.getUser(), config.getPass() ).rootUri( baseUrl ).build();
	}
}
