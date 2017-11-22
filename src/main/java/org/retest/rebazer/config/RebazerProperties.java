package org.retest.rebazer.config;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties("rebazer")
public class RebazerProperties {
	private String team;
	private String user;
	private String pass;
	private String workspace;
	private List<Repository> repos;

	@Data
	public static class Repository {
		private String name;
		private String branch;
		private String url;
		private CredentialsProvider credentials;
		private Git git;

		@Override
		public String toString() {
			return "Repository [name=" + name + ", branch=" + branch + "]";
		}
	}
}
