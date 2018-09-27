# rebazer

[![Build Status](https://travis-ci.com/retest/rebazer.svg?branch=develop)](https://travis-ci.com/retest/rebazer)
[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=org.retest%3Arebazer&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.retest%3Arebazer)
[![license](https://img.shields.io/badge/license-AGPL-brightgreen.svg)](https://github.com/retest/rebazer/blob/master/LICENSE)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-ff69b4.svg)](https://github.com/retest/rebazer/issues?q=is%3Aissue+is%3Aopen+label%3A%22help+wanted%22)
[![code with hearth by retest](https://img.shields.io/badge/%3C%2F%3E%20with%20%E2%99%A5%20by-retest-C1D82F.svg)](https://retest.de/en/)

Helper service to handle pull requests (PRs) on GitHub or Bitbucket. Primary it rebases the source branch of PRs against target branch to streamline commit history.

The rebazer polls in a configurable interval GitHub and Bitbucket repositories.
The processing for each repository is described below:

* Has PR changed since last run?
  * If no wait again for source/target branch, build result or approval change
* Is the corresponding build green?
  * If no wait again for green build
* Is source branch on top of target branch?
  * Rebase source branch
  * Conflict while rebasing?
    * If no abort rebase and comment PR
  * Wait for green build
* Is PR approved?
  * Wait for approval
* Merge PR into
* Delete merged branch

Illustrated as a flowchart:

![rebazer flowchart](src/doc/rebazer-flowchart.svg)


## Configuration

The `rebazer` relies on several parameters to configure the handled repositories.
The configuration can be specified in any way
[supported by spring boot](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html).
Recommended is to use a `application.yml` file. The roll out of the `application.yml` depends on type of [deployment](#Deployment).

Minimal configuration example:

  rebazer:
    hosts:
    - type: GITHUB
      teams:
      - name: your_company
        user: service_user
        pass: dont_use_this_pass_at_home
        repos:
        - name: foo
        - name: bar

An example in-depth can be found in [application-example.yml](./application-example.yml).

### Mandatory parameter

| Parameter                                 | Explanation                                              |
|-------------------------------------------|----------------------------------------------------------|
| `rebazer.hosts[ ].type`                   | Repository type, possible values `GITHUB` or `BITBUCKET` |
| `rebazer.hosts[ ].teams[ ].name`          | Team name for the repository access                      |
| `rebazer.hosts[ ].teams[ ].pass`          | Password for the repository access                       |
| `rebazer.hosts[ ].teams[ ].repos[ ].name` | Name/Key of a specific repository                        |

### Optional parameter

| Parameter                                         | Explanation                                      | Default Value                 |
|---------------------------------------------------|--------------------------------------------------|-------------------------------|
| `rebazer.workspace`                               | Workspace Directory for checkouts                | `./rebazer-workspace`         |
| `rebazer.garbageCollectionCountdown`              | Number of rebases before a git GC is triggered   | `20`                          |
| `rebazer.pollInterval`                            | Delay between each polling interval in seconds   | `60`                          |
| `rebazer.hosts[ ].url`                            | URL to the hosting service                       | Depents on `..hosts[ ].type`, e.g. github.org |
| `rebazer.hosts[ ].teams[ ].user`                  | User to log in for the specific team             | Same as `..teams[ ].name`     |
| `rebazer.hosts[ ].teams[ ].repos[ ].masterBranch` | Branch to reset git repo on cleanup after rebase | `master`                      |


## Deployment

### Spring Application JAR

This JAR can basically be run everywhere a JVM is present; there are no further dependencies. However, for a successful start, several parameters need to be configured (see configuration section for details). There are quite a number of [ways to specify](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-command-line-args) these parameters.

If e.g. all parameters are specified in a file called `application.yml`, the following command should be sufficient for starting `rebazer`:

`java -jar rebazer-VERSION.jar --spring.config.location=file:./application.yml`


### Debian package

**Attention**: We don't ship a fully functional configuration for this package. So after installation, one must ensure that a proper configuration is placed in `/etc/rebazer`. Also make sure that the configuration file is accessible by the user `rebazer`.

This package automatically creates:

* System user for running `rebazer`
* Proper logging via systemd/syslog (to `/var/log/rebazer`)
* Systemd service named `rebazer.service`
* Workspace in `/var/lib/rebazer` (can be configured in `/etc/defaults/rebazer`)

The debian package overwrites the `rebazer.workspace` parameter via an [environment](src/deb/etc/default/rebazer) variable.


#### Troubleshooting

>Symptom: Errors during startup

Solution: Please make sure the folder `/etc/rebazer` contains a valid configuration and the user `rebazer` has access to it.

    chmod 600 /etc/rebazer/application.yml
    chown rebazer:rebazer /etc/rebazer/application.yml


## Building

## Building prerequisites

Before building the `rebazer` application, please make sure that the following tools are installed:

* Java 8
* Maven (3.0+)
* Git


A typical build involves calling the `package` target via maven from the root of the cloned repository:


`mvn clean package`

Two major artifacts are then build inside the `target` directory: `rebazer-$VERSION.jar` and `rebazer_$VERSION_all.deb`.
