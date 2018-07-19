package org.retest.rebazer.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitRepoCleaner {

	private final int gcCountdownResetValue;
	private int gcCountdownCurrent;

	public GitRepoCleaner( final int gcCountdown ) {
		gcCountdownResetValue = gcCountdown;
		gcCountdownCurrent = gcCountdown;
	}

	public void cleanUp( final Git repoGit, final String fallbackBranchName ) {
		resetAndRemoveUntrackedFiles( repoGit );
		checkoutFallbackBranch( repoGit, fallbackBranchName );
		removeAllLocalBranches( repoGit );
		triggerGcIfNeeded( repoGit );
	}

	@SneakyThrows
	private void resetAndRemoveUntrackedFiles( final Git gitRepo ) {
		gitRepo.clean().setCleanDirectories( true ).setForce( true ).setIgnore( false ).call();
		gitRepo.reset().setMode( ResetType.HARD ).call();
	}

	@SneakyThrows
	private void checkoutFallbackBranch( final Git repoGit, final String fallbackBranchName ) {
		repoGit.checkout().setName( "remotes/origin/" + fallbackBranchName ).call();
	}

	@SneakyThrows
	private void removeAllLocalBranches( final Git repoGit ) {
		final String[] localBranches = repoGit.branchList() //
				.call().stream() //
				.filter( r -> r.getName() //
						.startsWith( "refs/heads/" ) ) //
				.map( r -> r.getName() ) //
				.toArray( String[]::new );

		repoGit.branchDelete().setForce( true ).setBranchNames( localBranches ).call();
	}

	@SneakyThrows
	private void triggerGcIfNeeded( final Git repoGit ) {
		gcCountdownCurrent--;
		if ( gcCountdownCurrent == 0 ) {
			gcCountdownCurrent = gcCountdownResetValue;
			log.info( "Running git gc on {}, next gc after {} cleanups.", repoGit, gcCountdownResetValue );
			repoGit.gc().setPrunePreserved( true ).setExpire( null ).call();
		}
	}

}
