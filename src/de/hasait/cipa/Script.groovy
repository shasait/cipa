/*
 * Copyright (C) 2017 by Sebastian Hasait (sebastian at hasait dot de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hasait.cipa

/**
 * Wrapper for WorkflowScript.
 */
class Script implements Serializable {

	def rawScript

	Script(rawScript) {
		this.rawScript = rawScript
	}

	String determineHostname() {
		def hostnameRaw = rawScript.sh(returnStdout: true, script: 'hostname')
		return hostnameRaw.trim()
	}

	/**
	 * Determine SVN URL of current working directory.
	 */
	String determineSvnUrlOfCwd() {
		String svnUrl = rawScript.sh(returnStdout: true, script: 'svn info | awk \'/^URL/{print $2}\'')
		return svnUrl.trim()
	}

	/**
	 * Determine SVN Revision of current working directory.
	 */
	String determineSvnRevOfCwd() {
		String svnRev = rawScript.sh(returnStdout: true, script: 'svn info | awk \'/^Revision/{print $2}\'')
		return svnRev.trim()
	}

	/**
	 * Determine Git Revision of current working directory.
	 */
	String determineGitRevOfCwd() {
		String gitRev = rawScript.sh(returnStdout: true, script: 'git rev-parse HEAD')
		return gitRev.trim()
	}

	void echo(String message) {
		rawScript.echo '[Script] ' + message
	}

	void dir(String dirname, Closure<?> body) {
		rawScript.dir(dirname, body)
	}

	void deleteDir() {
		rawScript.deleteDir()
	}

	String sh(String script, boolean returnStdout = false) {
		return rawScript.sh(script: script, returnStdout: returnStdout)
	}

	String shAsUser(String username, String script, boolean returnStdout = false) {
		return sh('echo "' + script.replace('"', '\\"') + '" | ssh -T -o "BatchMode yes" ' + username + '@localhost', returnStdout)
	}

	void timeout(int timeOutInMinutes, Closure<?> body) {
		rawScript.timeout(timeOutInMinutes, body)
	}

}
