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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * This class and it internal objects are primary to read the spring configuration. For repository configurations use
 * aggregated objects form {@link #getRepos()}.
 */
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

	private boolean changeDetection = false;

	private String branchMatcher = "^feature/.*";

	@Getter( AccessLevel.NONE )
	private List<Host> hosts;

	@Setter
	@EqualsAndHashCode
	static class Host {
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
	@EqualsAndHashCode
	static class Team {
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
	@EqualsAndHashCode
	static class Repo {
		String name;
		String masterBranch = "master";
	}

	/**
	 * This method converts the objects optimized for spring config to objects optimized for internal processing
	 * 
	 * @return List of all configured repos
	 */
	public List<RepositoryConfig> getRepos() {
		checkThatConfigurationIsReaded();

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

	private void checkThatConfigurationIsReaded() {
		if ( hosts == null || hosts.isEmpty() ) {
			throw new IllegalStateException( "No repositories defined, please verify that application.yml is placed"
					+ " at the correct location and is readable!" );
		}
	}

}
