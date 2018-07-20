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
	public GitRepoCleaner( final RebazerConfig repoConfig ) {
		gcCountdownResetValue = repoConfig.getGarbageCollectionCountdown();
		gcCountdownCurrent = gcCountdownResetValue;
	}

	public void cleanUp( final Git localRepo, final String fallbackBranchName ) {
		resetAndRemoveUntrackedFiles( localRepo );
		checkoutFallbackBranch( localRepo, fallbackBranchName );
		removeAllLocalBranches( localRepo );
		triggerGcIfNeeded( localRepo );
	}

	@SneakyThrows
	private void resetAndRemoveUntrackedFiles( final Git localRepo ) {
		localRepo.clean().setCleanDirectories( true ).setForce( true ).setIgnore( false ).call();
		localRepo.reset().setMode( ResetType.HARD ).call();
	}

	@SneakyThrows
	private void checkoutFallbackBranch( final Git localRepo, final String fallbackBranchName ) {
		localRepo.checkout().setName( "remotes/origin/" + fallbackBranchName ).call();
	}

	@SneakyThrows
	private void removeAllLocalBranches( final Git localRepo ) {
		final List<Ref> allBranches = localRepo.branchList().call();
		final String[] localBranches = allBranches.stream() //
				.map( branch -> branch.getName() ) //
				.filter( name -> name.startsWith( "refs/heads/" ) ) //
				.toArray( String[]::new );

		localRepo.branchDelete().setForce( true ).setBranchNames( localBranches ).call();
	}

	@SneakyThrows
	private void triggerGcIfNeeded( final Git localRepo ) {
		gcCountdownCurrent--;
		if ( gcCountdownCurrent == 0 ) {
			gcCountdownCurrent = gcCountdownResetValue;
			log.info( "Running git gc on {}, next gc after {} cleanups.", localRepo, gcCountdownResetValue );
			localRepo.gc().setPrunePreserved( true ).setExpire( null ).call();
		}
	}

}
