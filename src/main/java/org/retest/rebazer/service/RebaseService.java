package org.retest.rebazer.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryConfig;
import org.retest.rebazer.config.RebazerConfig.RepositoryHost;
import org.retest.rebazer.config.RebazerConfig.RepositoryTeam;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RebaseService {

	private final File workspace;
	private final GitRepoCleaner cleaner;

	private final Map<RepositoryConfig, CredentialsProvider> credentials = new HashMap<>();
	private final Map<RepositoryConfig, Git> localGitRepos = new HashMap<>();

	@Autowired
	public RebaseService( final RebazerConfig rebazerConfig, final GitRepoCleaner cleaner ) {
		this.cleaner = cleaner;
		workspace = new File( rebazerConfig.getWorkspace() ).getAbsoluteFile();

		rebazerConfig.getHosts().forEach( repoHost -> {
			repoHost.getTeams().forEach( repoTeam -> {
				repoTeam.getRepos().forEach( repoConfig -> {
					setupRepo( repoHost, repoTeam, repoConfig );
				} );
			} );
		} );
	}

	private void setupRepo( final RepositoryHost repoHost, final RepositoryTeam repoTeam,
			final RepositoryConfig repoConfig ) {
		final CredentialsProvider credential = repoCredential( repoTeam );
		final File repoFolder = repoFolder( repoHost, repoTeam, repoConfig );
		final String url = repoUrl( repoHost, repoTeam, repoConfig );

		final Git localRepo = setupLocalGitRepo( credential, repoFolder, url );

		credentials.put( repoConfig, credential );
		localGitRepos.put( repoConfig, localRepo );
		cleaner.cleanUp( localRepo, repoConfig.getMasterBranch() );
	}

	private static CredentialsProvider repoCredential( final RepositoryTeam repoTeam ) {
		return new UsernamePasswordCredentialsProvider( repoTeam.getUser(), repoTeam.getPass() );
	}

	private File repoFolder( final RepositoryHost repoHost, final RepositoryTeam repoTeam,
			final RepositoryConfig repoConfig ) {
		return FileUtils.getFile( workspace, repoHost.getUrl().getHost(), repoTeam.getName(), repoConfig.getName() );
	}

	private static String repoUrl( final RepositoryHost repoHost, final RepositoryTeam repoTeam,
			final RepositoryConfig repoConfig ) {
		return repoHost.getUrl() + "/" + repoTeam.getName() + "/" + repoConfig.getName() + ".git";
	}

	private static Git setupLocalGitRepo( final CredentialsProvider credential, final File repoFolder,
			final String repoUrl ) {
		if ( repoFolder.exists() ) {
			final Git localRepo = tryToOpenExistingRepoAndCheckRemote( repoFolder, repoUrl );
			if ( localRepo != null ) {
				return localRepo;
			}
			deleteDirectory( repoFolder );
		}
		return cloneNewRepo( repoFolder, repoUrl, credential );
	}

	@SneakyThrows
	private static void deleteDirectory( final File repoFolder ) {
		FileUtils.deleteDirectory( repoFolder );
	}

	private static Git tryToOpenExistingRepoAndCheckRemote( final File repoFolder, final String repoUrl ) {
		try {
			final Git localRepo = Git.open( repoFolder );
			if ( originIsRepoUrl( localRepo, repoUrl ) ) {
				return localRepo;
			} else {
				log.error( "Repo has wrong remote URL!" );
				return null;
			}
		} catch ( final Exception e ) {
			log.error( "Exception while open repo!", e );
			return null;
		}
	}

	@SneakyThrows
	private static boolean originIsRepoUrl( final Git localRepo, final String repoUrl ) {
		return localRepo.remoteList().call().stream() //
				.filter( r -> r.getName().equals( "origin" ) ) //
				.anyMatch( r -> remoteConfigContainsRepoUrl( r, repoUrl ) );

	}

	private static boolean remoteConfigContainsRepoUrl( final RemoteConfig remoteConfig, final String repoUrl ) {
		return remoteConfig.getURIs().stream().anyMatch( url -> url.toString().equals( repoUrl ) );
	}

	@SneakyThrows
	private static Git cloneNewRepo( final File repoFolder, final String repoUrl,
			final CredentialsProvider credentialsProvider ) {
		log.info( "Cloning repository {} to folder {} ...", repoUrl, repoFolder );
		return Git.cloneRepository().setURI( repoUrl ).setCredentialsProvider( credentialsProvider )
				.setDirectory( repoFolder ).call();
	}

	@SneakyThrows
	public boolean rebase( final RepositoryConfig repoConfig, final PullRequest pullRequest ) {
		log.info( "Rebasing {}.", pullRequest );

		final Git localRepo = localGitRepos.get( repoConfig );
		final CredentialsProvider credential = credentials.get( repoConfig );

		try {
			localRepo.fetch().setCredentialsProvider( credential ).setRemoveDeletedRefs( true ).call();
			localRepo.checkout().setCreateBranch( true ).setName( pullRequest.getSource() )
					.setStartPoint( "origin/" + pullRequest.getSource() ).call();

			final RebaseResult rebaseResult =
					localRepo.rebase().setUpstream( "origin/" + pullRequest.getDestination() ).call();

			switch ( rebaseResult.getStatus() ) {
				case UP_TO_DATE:
					log.warn( "Why rebasing up to date {}?", pullRequest );
					return true;

				case FAST_FORWARD:
					log.warn( "Why creating {} without changes?", pullRequest );
					localRepo.push().setCredentialsProvider( credential ).setForce( true ).call();
					return true;

				case OK:
					localRepo.push().setCredentialsProvider( credential ).setForce( true ).call();
					return true;

				case STOPPED:
					log.info( "Merge conflict in {}.", pullRequest );
					localRepo.rebase().setOperation( Operation.ABORT ).call();
					return false;

				default:
					localRepo.rebase().setOperation( Operation.ABORT ).call();
					throw new RuntimeException(
							"For " + pullRequest + " rebase causes an unexpected result: " + rebaseResult.getStatus() );
			}
		} finally {
			cleaner.cleanUp( localRepo, repoConfig.getMasterBranch() );
		}
	}

}
