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

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurper
import hudson.model.Job
import hudson.model.Run

/**
 * Wrapper for pipeline script allowing access to well known steps.
 */
class PScript implements Serializable {

	static final String MVN_LOG = 'mvn.log'

	def rawScript

	PScript(rawScript) {
		this.rawScript = rawScript
	}

	String determineHostname() {
		String hostnameRaw = rawScript.sh(returnStdout: true, script: 'hostname')
		return hostnameRaw.trim()
	}

	String determineMvnRepo() {
		String workspace = rawScript.env.WORKSPACE
		return workspace + '/.repo'
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

	void echoStacktrace(String message, Throwable throwable) {
		echo(message + ': ' + extractStacktrace(throwable))
	}

	@NonCPS
	private String extractStacktrace(Throwable throwable) {
		StringWriter sw = new StringWriter()
		PrintWriter pw = new PrintWriter(sw)
		throwable.printStackTrace(pw)
		pw.flush()
		return sw.toString()
	}

	public <V> V dir(String dirname, Closure<V> body) {
		rawScript.dir(dirname, body)
	}

	void deleteDir() {
		rawScript.deleteDir()
	}

	String pwd() {
		return rawScript.pwd()
	}

	String sh(String script, boolean returnStdout = false) {
		return rawScript.sh(script: script, returnStdout: returnStdout)
	}

	String shAsUser(String username, String script, boolean returnStdout = false) {
		String escapedScript = script.replace('\\', '\\\\').replace('"', '\\"')
		return sh('echo "' + escapedScript + '" | ssh -T -o "BatchMode yes" ' + username + '@localhost', returnStdout)
	}

	public <V> V timeout(int timeoutInMinutes, Closure<V> body) {
		rawScript.timeout(timeoutInMinutes, body)
	}

	public <V> V stage(String name, Closure<V> body) {
		rawScript.stage(name, body)
	}

	void writeFile(String filepath, String content, String encoding = 'UTF-8') {
		rawScript.writeFile(encoding: encoding, file: filepath, text: content)
	}

	String readFile(String filepath, String encoding = 'UTF-8') {
		return rawScript.readFile(encoding: encoding, file: filepath)
	}

	void stash(String id, Set<String> includes = [], Set<String> excludes = [], boolean useDefaultExcludes = true, boolean allowEmpty = false) {
		rawScript.stash(name: id, includes: includes.join(','), excludes: excludes.join(','), useDefaultExcludes: useDefaultExcludes, allowEmpty: allowEmpty)
	}

	void unstash(String id) {
		rawScript.unstash(id)
	}

	void sleep(int seconds) {
		rawScript.sleep(seconds)
	}

	void archiveArtifacts(String artifacts, String excludes = null, boolean allowEmptyArchive = false, boolean fingerprint = false, boolean onlyIfSuccessful = false, boolean defaultExcludes = true, boolean caseSensitive = true) {
		rawScript.archiveArtifacts(allowEmptyArchive: allowEmptyArchive, artifacts: artifacts, caseSensitive: caseSensitive, defaultExcludes: defaultExcludes, excludes: excludes, fingerprint: fingerprint, onlyIfSuccessful: onlyIfSuccessful)
	}

	@NonCPS
	int getCurrentBuildNumber() {
		return rawScript.currentBuild.number
	}

	@NonCPS
	Run<?, ?> getCurrentRawBuild() {
		return rawScript.currentBuild.rawBuild
	}

	String mvn(
			List<String> goals,
			List<String> profiles = [],
			List<String> arguments = [],
			List<String> options = [],
			boolean returnStdout = false) {
		def allArguments = ['-B', '-V', '-e']
		if (rawScript.env[Cipa.ENV_VAR___MVN_SETTINGS]) {
			allArguments.add('-s "${' + Cipa.ENV_VAR___MVN_SETTINGS + '}"')
		}
		if (rawScript.env[Cipa.ENV_VAR___MVN_TOOLCHAINS]) {
			allArguments.add('--global-toolchains "${' + Cipa.ENV_VAR___MVN_TOOLCHAINS + '}"')
		}
		allArguments.add('-Dmaven.repo.local="${' + Cipa.ENV_VAR___MVN_REPO + '}"')
		if (!profiles.empty) {
			allArguments.add('-P' + profiles.join(','))
		}
		allArguments.addAll(goals)
		allArguments.addAll(arguments)

		def allArgumentsString = allArguments.empty ? '' : allArguments.join(' ')

		def optionsString = options.join(' ')

		rawScript.withEnv(["${Cipa.ENV_VAR___MVN_OPTIONS}=${optionsString} ${rawScript.env[Cipa.ENV_VAR___MVN_OPTIONS] ?: ''}"]) {
			writeFile(MVN_LOG, "Executing: mvn ${allArgumentsString}")
			sh("printenv | sort | tee -a ${MVN_LOG}")
			return sh("set -o pipefail ; mvn ${allArgumentsString} | tee -a ${MVN_LOG}", returnStdout)
		}
	}

	@NonCPS
	List<String> collectDescriptions(Job<?, ?> job = currentRawBuild.parent) {
		List<String> descriptions = new ArrayList<>()
		// start with Job
		def current = job
		while (current != null && current.hasProperty('description')) {
			def description = current.description
			if (description instanceof String) {
				descriptions.add(description)
			}
			current = current.hasProperty('parent') ? current.parent : null
		}
		return descriptions
	}

	@NonCPS
	Map<String, Object> parseJsonBlocks(List<String> descriptions, String blockId) {
		String blockBeginMarker = "vvv ${blockId}.json vvv"
		String blockEndMarker = "^^^ ${blockId}.json ^^^"

		Map<String, Object> result = new LinkedHashMap<>()

		for (description in descriptions.reverse()) {
			int ioBeginOfStartKey = description.indexOf(blockBeginMarker)
			if (ioBeginOfStartKey >= 0) {
				int ioAfterStartKey = ioBeginOfStartKey + blockBeginMarker.length()
				int ioAfterEndKey = description.lastIndexOf(blockEndMarker)
				if (ioAfterStartKey < ioAfterEndKey) {
					String additionalEnvJSON = description.substring(ioAfterStartKey, ioAfterEndKey)
					Object parsedObject = new JsonSlurper().parseText(additionalEnvJSON)
					if (parsedObject instanceof Map) {
						Map<?, ?> parsedMap = (Map<?, ?>) parsedObject
						for (parsedMapEntry in parsedMap) {
							if (parsedMapEntry.key instanceof String) {
								result.put((String) parsedMapEntry.key, parsedMapEntry.value)
							}
						}
					}
				}
			}
		}

		return result
	}

}
