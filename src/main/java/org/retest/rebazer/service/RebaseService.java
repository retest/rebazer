package org.retest.rebazer.service;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.retest.rebazer.config.RebazerConfig;
import org.retest.rebazer.config.RebazerConfig.Repository;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RebaseService {

	private final RebazerConfig config;

	private int currentGcCountdown;

	public RebaseService( final RebazerConfig config ) {
		this.config = config;
		currentGcCountdown = config.getGarbageCollectionCountdown();

		final CredentialsProvider credentials =
				new UsernamePasswordCredentialsProvider( config.getUser(), config.getPass() );

		config.getRepos().forEach( repo -> {
			final File repoFolder = new File( config.getWorkspace(), repo.getName() );
			Git localRepo = null;
			final String repoUrl = "https://bitbucket.org/" + config.getTeam() + "/" + repo.getName() + ".git";
			repo.setUrl( repoUrl );
			repo.setCredentials( credentials );

			if ( repoFolder.exists() ) {
				localRepo = tryToOpenExistingRepoAndCheckRemote( repoFolder, repoUrl );
				if ( localRepo == null ) {
					try {
						FileUtils.deleteDirectory( repoFolder );
					} catch ( final IOException e ) {
						throw new RuntimeException( e );
					}
				}
			}
			if ( localRepo == null ) {
				localRepo = cloneNewRepo( repoFolder, repoUrl, credentials );
			}
			repo.setGit( localRepo );
			cleanUp( repo );
		} );

	}

	private static Git tryToOpenExistingRepoAndCheckRemote( final File repoFolder, final String expectedRepoUrl ) {
		try {
			final Git repo = Git.open( repoFolder );
			if ( originIsRepoUrl( repo, expectedRepoUrl ) ) {
				return repo;
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
	private static boolean originIsRepoUrl( final Git repo, final String expectedRepoUrl ) {
		return repo.remoteList().call().stream().anyMatch( r -> r.getName().equals( "origin" )
				&& r.getURIs().stream().anyMatch( url -> url.toString().equals( expectedRepoUrl ) ) );
	}

	@SneakyThrows
	private static Git cloneNewRepo( final File repoFolder, final String gitRepoUrl,
			final CredentialsProvider credentialsProvider ) {
		log.info( "Cloning repository {} to folder {} ...", gitRepoUrl, repoFolder );
		return Git.cloneRepository().setURI( gitRepoUrl ).setCredentialsProvider( credentialsProvider )
				.setDirectory( repoFolder ).call();
	}

	@SneakyThrows
	public void rebase( final Repository repo, final PullRequest pullRequest ) {
		log.info( "rebase " + pullRequest );
		try {
			final Git repository = repo.getGit();
			final CredentialsProvider credentials = repo.getCredentials();
			repository.fetch().setCredentialsProvider( credentials ).setRemoveDeletedRefs( true ).call();
			repository.checkout().setCreateBranch( true ).setName( pullRequest.getSource() )
					.setStartPoint( "origin/" + pullRequest.getSource() ).call();

			try {
				repository.rebase().setUpstream( "origin/" + pullRequest.getDestination() ).call();
				repository.push().setCredentialsProvider( credentials ).setForce( true ).call();
			} catch ( final WrongRepositoryStateException e ) {
				log.warn( "merge conflict for " + pullRequest + " " + repository.status().call().getChanged().stream()
						.map( l -> l.toString() ).reduce( "\n", String::concat ) );
				repository.rebase().setOperation( Operation.ABORT ).call();
			}
		} finally {
			cleanUp( repo );
		}
	}

	@SneakyThrows
	private void cleanUp( final Repository repo ) {
		final Git repository = repo.getGit();
		resetAndRemoveUntrackedFiles( repository );
		repository.checkout().setName( "remotes/origin/" + repo.getBranch() ).call();
		removeAllLocalBranches( repository );
		triggerGc( repository );
	}

	private void resetAndRemoveUntrackedFiles( final Git repository )
			throws GitAPIException, CheckoutConflictException {
		repository.clean() //
				.setCleanDirectories( true ) //
				.setForce( true ) //
				.setIgnore( false ) //
				.call(); //

		repository.reset() //
				.setMode( ResetType.HARD ) //
				.call(); //
	}

	private void removeAllLocalBranches( final Git repository )
			throws GitAPIException, NotMergedException, CannotDeleteCurrentBranchException {
		final String[] localBranches = repository.branchList() //
				.call().stream() //
				.filter( r -> r.getName() //
						.startsWith( "refs/heads/" ) ) //
				.map( r -> r.getName() ) //
				.toArray( String[]::new ); //

		repository.branchDelete() //
				.setForce( true ) //
				.setBranchNames( localBranches ) //
				.call(); //
	}

	private void triggerGc( final Git repository ) throws GitAPIException {
		currentGcCountdown--;
		if ( currentGcCountdown == 0 ) {
			currentGcCountdown = config.getGarbageCollectionCountdown();
			log.info( "Running git gc on {}, next gc after {} rebases.", repository, currentGcCountdown );
			repository.gc() //
					.setPrunePreserved( true ) //
					.setExpire( null ) //
					.call(); //
		}
	}

}
