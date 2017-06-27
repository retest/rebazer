package org.retest.rebazer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;

@Configuration
public class ReBaZerConfig {

	@Value("${rebazer.repo.user}")
	private String username;

	@Value("${rebazer.repo.pass}")
	private String password;

	@Bean
	public RestOperations restOperations(RestTemplateBuilder restTemplateBuilder) {
		return restTemplateBuilder.basicAuthorization(username, password).build();
	}
}