package org.retest.rebazer.config;

import java.net.URL;
import java.util.List;

import org.retest.rebazer.KnownProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties( "rebazer" )
public class RebazerConfig {

	/**
	 * Values used for {@link org.retest.rebazer.RebazerService#pollToHandleAllPullRequests()}
	 */
	public final static String POLL_INTERVAL_KEY = "rebazer.pollInterval";
	public final static int POLL_INTERVAL_DEFAULT = 60;
	private long pollInterval = POLL_INTERVAL_DEFAULT;

	private String workspace = "./rebazer-workspace";
	private int garbageCollectionCountdown = 20;

	private List<RepositoryHost> hosts;

	@Data
	public static class RepositoryHost {
		private KnownProvider type;
		private URL url;
		private List<Team> teams;
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
	}

}
