def printIp() {
	sh 'ip addr'
}

def checkoutAndEnforceCleanWorkingSpace() {
	timeout(10) {
		checkout scm
		sh 'git clean -dfx'
	}
}

def runMavenCompile() {
	runMaven('compile test-compile', 8)
}

def runMaven(param = '', timeoutMinutes) {
	def java8Home = tool 'JDK 1.8'
	def mvnHome = tool 'Maven 3.5'

	withEnv(["JAVA_HOME=${java8Home}"]) {
		timeout(timeoutMinutes) {
			sh "${mvnHome}/bin/mvn -X -e -B ${param}"
		}
	}
}

node('linux') {
	timeout(60) {
		stage('Checkout') {
			printIp()
			checkoutAndEnforceCleanWorkingSpace()
		}

		stage('Build') {
			runMavenCompile()
		}

		stage('Tests') {
			runMaven('test', 10)
		}
	}
}
