package org.retest.rebazer.service;

import org.retest.rebazer.domain.PullRequest;
import org.springframework.stereotype.Service;

@Service
public class RebaseService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RebaseService.class);

	public void rebase(PullRequest pullRequest) {
		logger.warn("rebase " + pullRequest);
		// TODO Auto-generated method stub
	}

}
