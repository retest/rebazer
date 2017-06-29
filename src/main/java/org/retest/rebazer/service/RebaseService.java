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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RebaseService {

	private final CredentialsProvider credentialsProvider;
	private final Git repo;

	public RebaseService(@Autowired ReBaZerConfig config) {
		credentialsProvider = new UsernamePasswordCredentialsProvider(config.getUser(), config.getPass());

		final File repoFolder = new File(config.getWorkspace(), "repo");
		Git repo = null;

		if (repoFolder.exists()) {
			repo = tryToOpenExistingRepoAndCheckRemote(repoFolder, config.getRepoUrl());
			if (repo == null) {
				try {
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
		cleanUp();
	}

	private static Git tryToOpenExistingRepoAndCheckRemote(File repoFolder, String expectedRepoUrl) {
		try {
			final Git repo = Git.open(repoFolder);
			if (originIsRepoUrl(repo, expectedRepoUrl)) {
				return repo;
			} else {
				log.error("Repo has wrong remote URL!");
				return null;
			}
		} catch (final Exception e) {
			log.error("Exception while open repo!", e);
			return null;
		}
	}

	@SneakyThrows
	private static boolean originIsRepoUrl(Git repo, String expectedRepoUrl) {
		return repo.remoteList().call().stream().anyMatch(r -> r.getName().equals("origin")
				&& r.getURIs().stream().anyMatch(url -> url.toString().equals(expectedRepoUrl)));
	}

	@SneakyThrows
	private static Git cloneNewRepo(File repoFolder, String gitRepoUrl, CredentialsProvider credentialsProvider) {
		log.info("Checkout repo, this can take some time!");
		return Git.cloneRepository().setURI(gitRepoUrl).setCredentialsProvider(credentialsProvider)
				.setDirectory(repoFolder).call();
	}

	@SneakyThrows
	public synchronized void rebase(PullRequest pullRequest) {
		log.info("rebase " + pullRequest);
		try {

			repo.fetch().setCredentialsProvider(credentialsProvider).setRemoveDeletedRefs(true).call();
			repo.checkout().setCreateBranch(true).setName(pullRequest.getSource())
					.setStartPoint("origin/" + pullRequest.getSource()).call();

			try {
				repo.rebase().setUpstream("origin/" + pullRequest.getDestination()).call();
				repo.push().setCredentialsProvider(credentialsProvider).setForce(true).call();
			} catch (final WrongRepositoryStateException e) {
				log.warn("merge conflict for " + pullRequest + " " + repo.status().call().getChanged().stream()
						.map(l -> l.toString()).reduce("\n", String::concat));
				repo.rebase().setOperation(Operation.ABORT).call();
			}

		} finally {
			cleanUp();
		}
	}

	@SneakyThrows
	private void cleanUp() {
		repo.clean().setCleanDirectories(true).setForce(true).setIgnore(false).call();
		repo.reset().setMode(ResetType.HARD).call();
		repo.checkout().setName("remotes/origin/develop").call();

		final String[] localBranches = repo.branchList().call().stream()
				.filter(r -> r.getName().startsWith("refs/heads/")).map(r -> r.getName()).toArray(String[]::new);
		repo.branchDelete().setForce(true).setBranchNames(localBranches).call();

		repo.gc().setPrunePreserved(true).setExpire(null).call();
	}

}
