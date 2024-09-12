/*
 * Copyright (C) 2024 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.tool

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.activity.AbstractCipaBean

class MavenExecution extends AbstractCipaBean {

	static final String MVN_LOG_FILE = 'mvn.log'
	static final String MVN_DEFAULT_REPO_RELDIR = '.repo'
	static final List MVN_DEFAULT_STDOUT_FILTERS = ['[INFO] Building', '[INFO] BUILD ', '[INFO] Finished at']

	private List<String> goals = []
	private List<String> profiles = []
	private List<String> arguments = []
	private List<String> options = []
	private List<String> stdoutFilters = MVN_DEFAULT_STDOUT_FILTERS
	private List<String> preMvnStatements = []

	private String privateRepo

	private int retriesIfRepoAccessRace = 2

	MavenExecution(rawScriptOrCipa) {
		super(rawScriptOrCipa, false)
	}

	@NonCPS
	MavenExecution addGoals(List<String> goals) {
		this.goals.addAll(goals)
		return this
	}

	@NonCPS
	MavenExecution addProfiles(List<String> profiles) {
		this.profiles.addAll(profiles)
		return this
	}

	@NonCPS
	MavenExecution addArguments(List<String> arguments) {
		this.arguments.addAll(arguments)
		return this
	}

	@NonCPS
	MavenExecution addOptions(List<String> options) {
		this.options.addAll(options)
		return this
	}

	@NonCPS
	MavenExecution addStdoutFilters(List<String> stdoutFilters) {
		this.stdoutFilters.addAll(stdoutFilters)
		return this
	}

	@NonCPS
	MavenExecution addPreMvnStatements(List<String> preMvnStatements) {
		this.preMvnStatements.addAll(preMvnStatements)
		return this
	}

	@NonCPS
	MavenExecution replaceStdoutFilters(List<String> stdoutFilters) {
		this.stdoutFilters.clear()
		this.stdoutFilters.addAll(stdoutFilters)
		return this
	}

	/**
	 * Use folder <code>relDir</code> in current working directory as mvn repo (will be created automatically).
	 */
	MavenExecution withPrivateRepo(String relDir = MVN_DEFAULT_REPO_RELDIR) {
		privateRepo = script.pwd() + '/' + relDir
		return this
	}

	/**
	 * Use folder <code>absDir</code> as mvn repo (will be created automatically).
	 */
	@NonCPS
	MavenExecution withAbsolutePrivateRepo(String absDir) {
		privateRepo = absDir
		return this
	}

	@NonCPS
	MavenExecution withRetriesIfRepoAccessRace(int retriesIfRepoAccessRace) {
		this.retriesIfRepoAccessRace = retriesIfRepoAccessRace
		return this
	}

	String execute(boolean returnStdout = false) {
		if (privateRepo != null) {
			def envVars = []
			envVars.add("${Cipa.ENV_VAR___MVN_REPO}=${privateRepo}")
			rawScript.withEnv(envVars) {
				return executeInternalArgs(returnStdout)
			}
		} else {
			return executeInternalArgs(returnStdout)
		}

	}

	private String executeInternalArgs(boolean returnStdout) {
		if (goals.empty) {
			throw new RuntimeException('goals is empty')
		}

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

		String allArgumentsString = allArguments.empty ? '' : allArguments.join(' ')
		String optionsString = options.join(' ')
		String grepString = buildGrep(stdoutFilters)

		rawScript.withEnv(["${Cipa.ENV_VAR___MVN_OPTIONS}=${optionsString} ${rawScript.env[Cipa.ENV_VAR___MVN_OPTIONS] ?: ''}"]) {
			return executeInternalSh(allArgumentsString, grepString, returnStdout, 0)
		}
	}

	private String executeInternalSh(String allArgumentsString, String grepString, boolean returnStdout, int currentTry) {
		script.writeFile(MVN_LOG_FILE, "Executing: mvn ${allArgumentsString}\nEnvironment:\n")
		try {
			return script.sh("#!/bin/bash\n${preMvnStatements.join('\n')}\nprintenv | sort | tee -a '${MVN_LOG_FILE}'\nset -o pipefail\nmvn ${allArgumentsString} | tee -a '${MVN_LOG_FILE}' ${grepString}", returnStdout)
		} catch (e) {
			if (currentTry < retriesIfRepoAccessRace && privateRepo == null) {
				String matchedLines = script.sh("cat '${MVN_LOG_FILE}' | grep 'Caused by: java.io.FileNotFoundException:' | grep '.part (No such file or directory)' || true", true)
				if (matchedLines.readLines().size() > 0) {
					script.echo('Retry after repo access race:\n' + matchedLines)
					script.sleep(5)
					return executeInternalSh(allArgumentsString, grepString, returnStdout, currentTry + 1)
				}
			}
			throw e
		}
	}

	@NonCPS
	private static String buildGrep(List<String> filters) {
		if (filters) {
			return '| grep -E "' + filters.collect { it.replace('[', '\\[').replace(']', '\\]').replace('"', '\\"') }.join('|') + '"'
		}
		return ''
	}

}
