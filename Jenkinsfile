def name = "rebazer"

pipeline {
	environment {
		MVN_ARGS = "--batch-mode --errors --fail-fast"
		CREDENTIALS = credentials("dockerhub")

		API_KEY = credentials("jenkins-digitalocean-api")
		NAMESPACE="${name}"
		DEPLOYMENT="${name}"
		CONTAINER="${name}"
		IMAGE="retest/${name}"
	}

	tools {
		jdk "OpenJDK 11"
		maven "3.6"
	}

	agent {
		label "linux"
	}

	stages {
		stage('Build') {
		   steps {
   				sh "mvn ${MVN_ARGS} clean compile test-compile"
   			}
		}

		stage('Test') {
		   steps {
   				sh "mvn ${MVN_ARGS} verify"
   			}
		}

		stage('Package') {
		   steps {
   				sh "mvn -Pjib ${MVN_ARGS} compile jib:build -Djib.to.auth.username=${CREDENTIALS_USR} -Djib.to.auth.password=${CREDENTIALS_PSW}"
   			}
		}

		stage("Deploy dev") {
			when {
				branch "develop"
			}
			environment {
				CLUSTER = "dev-cluster-01"
			}
			steps {
				script {
					deploy()
				}
			}
			post {
				always {
				 	sendNotifications(currentBuild.result)
				}
			}
		}

		stage("Deploy prod") {
			when {
				branch "master"
			}
			environment {
				CLUSTER = "prod-cluster-01"
			}
			steps {
				script {
					deploy()
				}
			}
			post {
				always {
				 	sendNotifications(currentBuild.result)
				}
			}
		}
	}
}


def deploy() {
	TAG = sh(script: 'git describe', , returnStdout: true).trim()
	sh """
		doctl --access-token="${API_KEY}" auth init
		doctl kubernetes cluster kubeconfig save "${CLUSTER}"
		kubectl config use-context "do-fra1-${CLUSTER}"
		kubectl --namespace="${NAMESPACE}" set image "deployment/${DEPLOYMENT}" "${CONTAINER}=${IMAGE}:${TAG}"

		# Cleanup local config
		rm -rf "${HOME}/.kube/"
		rm -rf "${HOME}/.config/doctl/"
	"""
}

def sendNotifications(String buildStatus) {
	if (buildStatus == 'SUCCESS') {
		slackSend channel: 'ops', color: 'good', message: "Successfully deployed *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
	} else {
		slackSend channel: 'ops', color: 'warning', message: "Error during deployment of *${DEPLOYMENT}* (${TAG}) to ${CLUSTER}.", tokenCredentialId: 'slack-token'
	}
}
