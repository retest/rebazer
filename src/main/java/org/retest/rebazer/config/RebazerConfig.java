package org.retest.rebazer.config;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.retest.rebazer.RepositoryHostingTypes;
import org.retest.rebazer.domain.RepositoryConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Configuration
@ConfigurationProperties( "rebazer" )
public class RebazerConfig {

	/**
	 * Values used for {@link org.retest.rebazer.RebazerService#pollToHandleAllPullRequests()}
	 */
	public static final String POLL_INTERVAL_KEY = "rebazer.pollInterval";
	public static final int POLL_INTERVAL_DEFAULT = 60;
	private long pollInterval = POLL_INTERVAL_DEFAULT;

	private String workspace = "rebazer-workspace";
	private int garbageCollectionCountdown = 20;

	@Getter( AccessLevel.NONE )
	private List<Host> hosts;

	@Setter
	private static class Host {
		RepositoryHostingTypes type;
		private URL url;
		List<Team> teams;

		public URL getUrl() {
			if ( url != null ) {
				return url;
			} else {
				return type.getDefaultUrl();
			}
		}

	}

	@Setter
	private static class Team {
		String name;
		private String user;
		String pass;
		List<Repo> repos;

		public String getUser() {
			if ( user != null && !user.isEmpty() ) {
				return user;
			} else {
				return name;
			}
		}
	}

	@Setter
	private static class Repo {
		String name;
		String masterBranch = "master";
	}

	/**
	 * This method converts the objects optimized for spring config to objects optimized for internal processing
	 */
	public List<RepositoryConfig> getRepos() {
		final List<RepositoryConfig> configs = new ArrayList<>();
		for ( final Host host : hosts ) {
			for ( final Team team : host.teams ) {
				for ( final Repo repo : team.repos ) {
					configs.add( RepositoryConfig.builder() //
							.type( host.type ).host( host.getUrl() ) //
							.team( team.name ).repo( repo.name ) //
							.user( team.getUser() ).pass( team.pass ) //
							.masterBranch( repo.masterBranch ) //
							.build() );
				}
			}
		}
		return configs;
	}

}
