package org.retest.rebazer.config;

import java.net.URL;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties( "rebazer" )
public class RebazerConfig {
	private String workspace = "./rebazer-workspace";
	private int garbageCollectionCountdown = 20;
	private long pollInterval;
	private List<RepositoryHost> hosts;

	@Data
	public static class RepositoryHost {
		private String type;
		private URL url;
		private List<Team> team;
	}

	@Data
	public static class Team {
		private String name;
		private String user;
		private String pass;
		private List<RepositoryConfig> repos;
	}

	@Data
	public static class RepositoryConfig {
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

}
