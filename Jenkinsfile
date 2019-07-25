pipeline {
	environment {
		CREDENTIALS = credentials("dockerhub")
		API_KEY = credentials("jenkins-digitalocean-api")
		NAMESPACE="rebazer"
		DEPLOYMENT="rebazer"
		CONTAINER="rebazer"
		IMAGE="retest/rebazer"
		TAG = sh(script: 'git rev-parse --short HEAD', , returnStdout: true).trim()
	}

	tools {
		jdk "OpenJDK 11"
		maven "3.6"
	}

	agent {
		label "linux-docker"
	}

	stages {
		stage('Build') {
		   steps {
   				sh "mvn --batch-mode clean compile test-compile"
   			}
		}

		stage('Test') {
		   steps {
   				sh "mvn --batch-mode verify"
   			}
		}

		stage('Package') {
		   steps {
   				sh "mvn --batch-mode compile jib:build -Djib.to.auth.username=${CREDENTIALS_USR} -Djib.to.auth.password=${CREDENTIALS_PSW}"
   			}
		}

		stage("Deploy dev") {
			when {
				branch "develop"
			}
			steps {
				script {
					CLUSTER = "dev-cluster-01"
				}
				sh "ci/deploy.sh ${CLUSTER} ${TAG}"
			}
			post {
				success {
					slackSend channel: 'ops', color: 'good', message: "Successfully deployed *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
				}
				failure {
					slackSend channel: 'ops', color: 'warning', message: "Error during deployment of *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
				}
			}
		}

		stage("Deploy prod") {
			when {
				branch "master"
			}
			steps {
				script {
					CLUSTER = "prod-cluster-01"
				}
				sh "ci/deploy.sh ${CLUSTER} ${TAG}"
			}
			post {
				success {
					slackSend channel: 'ops', color: 'good', message: "Successfully deployed *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
				}
				failure {
					slackSend channel: 'ops', color: 'warning', message: "Error during deployment of *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
				}
			}
		}
	}
}
