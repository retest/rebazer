package org.retest.rebazer.service;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.retest.rebazer.config.ReBaZerConfig;
import org.retest.rebazer.domain.PullRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.SneakyThrows;

@Service
public class RebaseService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RebaseService.class);

	private final CredentialsProvider credentialsProvider;
	private final Git repo;

	public RebaseService(@Autowired ReBaZerConfig config) {
		credentialsProvider = new UsernamePasswordCredentialsProvider(config.getUserName(), config.getPassword());

		final File repoFolder = new File(config.getWorkspace(), "repo");
		Git repo = null;

		if (repoFolder.exists()) {
			repo = tryToOpenExistingRepoAndCheckRemote(repoFolder, config.getRepoUrl());
			if (repo == null) {
				try {
					logger.warn("Remove repo with wrong remote URL!");
					FileUtils.deleteDirectory(repoFolder);
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (repo == null) {
			repo = cloneNewRepo(repoFolder, config.getRepoUrl(), credentialsProvider);
		}
		this.repo = repo;
	}

	@SneakyThrows
	private static Git tryToOpenExistingRepoAndCheckRemote(File repoFolder, String expectedRepoUrl) {
		final Git repo = Git.open(repoFolder);
		if (check(repo, expectedRepoUrl)) {
			return repo;
		} else {
			return null;
		}
	}

	@SneakyThrows
	private static boolean check(Git repo, String expectedRepoUrl) {
		return repo.remoteList().call().stream().anyMatch(r -> r.getName().equals("origin")
				&& r.getURIs().stream().anyMatch(url -> url.toString().equals(expectedRepoUrl)));
	}

	@SneakyThrows
	private static Git cloneNewRepo(File repoFolder, String gitRepoUrl, CredentialsProvider credentialsProvider) {
		logger.info("Checkout repo, this can take some time!");
		return Git.cloneRepository().setURI(gitRepoUrl).setCredentialsProvider(credentialsProvider)
				.setDirectory(repoFolder).call();
	}

	@SneakyThrows
	public synchronized void rebase(PullRequest pullRequest) {
		logger.warn("rebase " + pullRequest);

		repo.fetch().setCredentialsProvider(credentialsProvider).setRemoveDeletedRefs(true).call();
		repo.checkout().setName("origin/" + pullRequest.getSource()).call();
		repo.checkout().setCreateBranch(true).setName(pullRequest.getSource()).call();

		try {
			repo.rebase().setUpstream("origin/" + pullRequest.getDestination()).call();
			repo.push().setCredentialsProvider(credentialsProvider).setForce(true).call();
		} catch (final WrongRepositoryStateException e) {
			logger.error("merge conflict for " + pullRequest + " "
					+ repo.status().call().getChanged().stream().map(l -> l.toString()).reduce("\n", String::concat));
			repo.rebase().setOperation(Operation.ABORT).call();
			repo.reset().setMode(ResetType.HARD).call();
			// TODO comment PR with info about merge conflict
		}

		repo.checkout().setName("origin/develop").call();
		repo.branchDelete().setForce(true).setBranchNames(pullRequest.getSource()).call();
		repo.gc().call(); // TODO run separate in aggressive mode every 10-20
							// rebases
	}

}
