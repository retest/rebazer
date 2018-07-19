package org.retest.rebazer.service;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.Ref;
import org.retest.rebazer.config.RebazerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitRepoCleaner {

	private final int gcCountdownResetValue;
	private int gcCountdownCurrent;

	@Autowired
	public GitRepoCleaner( final RebazerConfig config ) {
		gcCountdownResetValue = config.getGarbageCollectionCountdown();
		gcCountdownCurrent = gcCountdownResetValue;
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
		final List<Ref> allBranches = repoGit.branchList().call();
		final String[] localBranches = allBranches.stream() //
				.map( branch -> branch.getName() ) //
				.filter( name -> name.startsWith( "refs/heads/" ) ) //
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
