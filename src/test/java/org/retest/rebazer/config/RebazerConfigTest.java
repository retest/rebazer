package org.retest.rebazer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.retest.rebazer.RepositoryHostingTypes;
import org.retest.rebazer.config.RebazerConfig.Host;
import org.retest.rebazer.config.RebazerConfig.Repo;
import org.retest.rebazer.config.RebazerConfig.Team;

class RebazerConfigTest {

	RebazerConfig cut;

	Host host;
	Team team;
	Repo repo;

	@BeforeEach
	void setUp() {
		cut = createCut();
	}

	private RebazerConfig createCut() {
		repo = new Repo();
		repo.name = "repoName";

		team = new Team();
		team.name = "teamName";
		team.pass = "teamPass";
		team.setRepos( Arrays.asList( repo ) );

		host = new Host();
		host.type = RepositoryHostingTypes.GITHUB;
		host.setTeams( Arrays.asList( team ) );

		final RebazerConfig cut = new RebazerConfig();
		cut.setHosts( Arrays.asList( host ) );

		return cut;
	}

	@Test
	void equals_should_be_steady() {
		assertThat( cut.equals( cut ) ).isTrue();
		assertThat( cut.equals( createCut() ) ).isTrue();
		assertThat( cut.equals( null ) ).isFalse();
		assertThat( cut.equals( new RebazerConfig() ) ).isFalse();
	}

	@Test
	void hashCode_should_be_steady() {
		assertThat( cut.hashCode() ).isEqualTo( cut.hashCode() );
		assertThat( cut.hashCode() ).isEqualTo( createCut().hashCode() );
		assertThat( cut.hashCode() ).isNotEqualTo( new RebazerConfig().hashCode() );
	}

	@Test
	void host_getURL_can_handle_missing_URL() {
		assertThat( host.getGitHost() ).isNotNull();
	}

	@Test
	void host_getURL_return_URL_if_exists() throws MalformedURLException {
		final URL url = new URL( "http://test" );
		host.setGitHost( url );

		assertThat( host.getGitHost() ).isEqualTo( url );
	}

	@Test
	void getRepos_throws_Exception_if_it_is_not_filled_with_repositories() {
		cut = new RebazerConfig();
		assertThatThrownBy( () -> cut.getRepos() ).isExactlyInstanceOf( IllegalStateException.class )
				.hasMessageContaining( "No repositories defined" );
	}

}
