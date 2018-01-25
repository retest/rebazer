package org.retest.rebazer.config;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.retest.rebazer.service.Provider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties( "rebazer" )
public class RebazerConfig {
	private List<RepositoryHost> hosts;
	private String workspace = "./rebazer-workspace";
	private int garbageCollectionCountdown = 20;
	private int pollInterval;

	@Data
	public static class Repository {
		private String name;
		private String branch;
		private String url;
		private CredentialsProvider credentials;
		private Git git;

		@Override
		public String toString() {
			return "Repo " + name + " (" + url + ")";
		}
	}

	@Data
	public static class Team {
		private String id;
		private String name;
		private String user;
		private String pass;
		private List<Repository> repos;
	}

	@Data
	public static class RepositoryHost {
		private String type;
		private String url;
		private List<Team> team;
		private Provider provider;
	}
}
