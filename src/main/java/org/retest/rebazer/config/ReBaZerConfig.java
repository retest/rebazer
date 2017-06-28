package org.retest.rebazer.config;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestOperations;

import lombok.Getter;

@Configuration
public class ReBaZerConfig {

	@Getter
	@Value("${rebazer.repo.user}")
	String user;

	@Getter
	@Value("${rebazer.repo.pass}")
	String pass;

	@Value("${rebazer.repo.team}")
	String team;

	@Value("${rebazer.repo.repo}")
	String repo;

	@Value("${rebazer.workspace}")
	String workspace;

	@Bean
	public RestOperations restOperations(RestTemplateBuilder restTemplateBuilder) {
		return restTemplateBuilder.basicAuthorization(user, pass).build();
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

}