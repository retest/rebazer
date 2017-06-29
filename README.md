# rebazer

Helper service to handle PullRequests on Bitbucket.
Rebase PullRequests against target to streamline commit history.
Merge the PullRequest if it's rebased, approved and the build is green.


## Open tasks

* Comment PR with info about merge conflicts.
* On merge conflict don't try to rebase as as long there are no changes on branch.
* Run git garbage collection only every 20-50 rebases.
* Implement some tests.

