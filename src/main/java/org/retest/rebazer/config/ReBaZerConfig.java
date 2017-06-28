package org.retest.rebazer.config;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;

@Configuration
public class ReBaZerConfig {

	@Value("${rebazer.repo.user}")
	String username;

	@Value("${rebazer.repo.pass}")
	String password;

	@Value("${rebazer.repo.team}")
	String team;

	@Value("${rebazer.repo.repo}")
	String repo;

	@Value("${rebazer.workspace}")
	String workspace;

	@Bean
	public RestOperations restOperations(RestTemplateBuilder restTemplateBuilder) {
		return restTemplateBuilder.basicAuthorization(username, password).build();
	}

	public String getApiBaseUrl() {
		return "https://api.bitbucket.org/2.0/repositories/" + team + "/" + repo + "/";
	}

	public String getRepoUrl() {
		return "https://bitbucket.org/" + team + "/" + repo + ".git";
	}

	public File getWorkspace() {
		return new File(workspace);
	}

	public String getUserName() {
		return username;
	}

	public String getPassword() {
		return password;
	}

}