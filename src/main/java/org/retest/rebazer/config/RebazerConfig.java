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
		private List<RepositoryTeam> teams;

		public URL getUrl() {
			if ( url != null ) {
				return url;
			} else {
				return type.getDefaultUrl();
			}
		}
	}

	@Data
	public static class RepositoryTeam {
		private String name;
		private String user;
		private String pass;
		private List<RepositoryConfig> repos;

		public String getUser() {
			if ( user != null && !user.isEmpty() ) {
				return user;
			} else {
				return name;
			}
		}
	}

	@Data
	public static class RepositoryConfig {
		private String name;
		private String masterBranch = "master";
	}

}
