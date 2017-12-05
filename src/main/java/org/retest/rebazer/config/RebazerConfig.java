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
public class RebazerConfig {
	private final String team;
	private final String user;
	private final String pass;
	private final String workspace;
	private final List<Repository> repos;
	private final int garbageCollectionCountdown;

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
