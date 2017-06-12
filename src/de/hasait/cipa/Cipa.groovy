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

/**
 *
 */
class Cipa implements Serializable {

	private static final String ENV_VAR___JDK_HOME = 'JAVA_HOME'
	private static final String ENV_VAR___MVN_HOME = 'M2_HOME'
	private static final String ENV_VAR___MVN_REPO = 'MVN_REPO'
	private static final String ENV_VAR___MVN_SETTINGS = 'MVN_SETTINGS'
	private static final String ENV_VAR___MVN_TOOLCHAINS = 'MVN_TOOLCHAINS'
	private static final String ENV_VAR___MVN_NODE_OPTIONS = 'MVN_NODE_OPTS'

	private final def script

	private CipaTool toolJdk
	private CipaTool toolMvn

	private final List<CipaTool> tools = new ArrayList<>()
	private final List<CipaNode> nodes = new ArrayList<>()
	private final List<CipaActivity> activities = new ArrayList<>()

	Cipa(script) {
		if (!script) {
			throw new IllegalArgumentException('script')
		}
		this.script = script;
	}

	@NonCPS
	CipaNode newNode(String nodeLabel) {
		CipaNode node = new CipaNode(nodeLabel)
		nodes.add(node)
		return node
	}

	@NonCPS
	CipaActivity newActivity(CipaNode node, String description, Closure body) {
		CipaActivity activity = new CipaActivity(node, description, body)
		activities.add(activity)
		return activity
	}

	CipaTool configureJDK(String version) {
		if (!toolJdk) {
			toolJdk = new CipaTool()
			tools.add(toolJdk)
		}
		toolJdk.name = version
		toolJdk.type = 'hudson.model.JDK'
		toolJdk.addToPathWithSuffix = '/bin'
		toolJdk.dedicatedEnvVar = ENV_VAR___JDK_HOME
		return toolJdk
	}

	CipaTool configureMaven(String version, String mvnSettingsFileId = null, String mvnToolchainsFileId = null) {
		if (!toolMvn) {
			toolMvn = new CipaTool()
			tools.add(toolMvn)
		}
		toolMvn.name = version
		toolMvn.type = 'hudson.tasks.Maven$MavenInstallation'
		toolMvn.addToPathWithSuffix = '/bin'
		toolMvn.dedicatedEnvVar = ENV_VAR___MVN_HOME
		if (mvnSettingsFileId) {
			toolMvn.addConfigFileEnvVar(ENV_VAR___MVN_SETTINGS, mvnSettingsFileId)
		}
		if (mvnToolchainsFileId) {
			toolMvn.addConfigFileEnvVar(ENV_VAR___MVN_TOOLCHAINS, mvnToolchainsFileId)
		}
		return toolMvn
	}

	CipaTool configureTool(String name, String type) {
		CipaTool tool = new CipaTool()
		tool.name = name
		tool.type = type
		tools.add(tool)
		return tool
	}

	private Closure parallelNodeWithActivitiesBranch(CipaNode node, List<CipaActivity> nodeActivities) {
		return {
			def parallelActivitiesBranches = [:]
			for (int i = 0; i < nodeActivities.size(); i++) {
				CipaActivity activity = nodeActivities.get(i)
				parallelActivitiesBranches["${i}-${activity.description}"] = parallelActivityRunBranch(activity)
			}

			nodeWithEnv(node) {
				parallelActivitiesBranches.failFast = true
				script.parallel(parallelActivitiesBranches)
			}
		}
	}

	private Closure parallelActivityRunBranch(CipaActivity activity) {
		return {
			script.waitUntil() {
				activity.readyToRunActivity()
			}
			activity.runActivity()
			if (activity.failedThrowable) {
				StringWriter sw = new StringWriter()
				PrintWriter pw = new PrintWriter(sw)
				activity.failedThrowable.printStackTrace(pw)
				pw.flush()
				script.echo(sw.toString())
			}
		}
	}

	void runActivities() {
		script.echo("[CIPActivities] Running...")

		def parallelNodeBranches = [:]
		for (int i = 0; i < nodes.size(); i++) {
			CipaNode node = nodes.get(i)
			List<CipaActivity> nodeActivities = new ArrayList<>()
			for (activity in activities) {
				if (activity.node.is(node)) {
					nodeActivities.add(activity)
				}
			}
			parallelNodeBranches["${i}-${node.nodeLabel}"] = parallelNodeWithActivitiesBranch(node, nodeActivities)
		}

		script.stage('Pipeline') {
			parallelNodeBranches.failFast = true
			script.parallel(parallelNodeBranches)
		}

		script.echo("[CIPActivities] Done")

		for (activity in activities) {
			script.echo("[CIPActivities] Activity: ${activity.description}")
			script.echo("[CIPActivities]     ${activity.buildStateHistoryString()}")
		}

		CipaActivity.throwOnAnyActivityFailure('Activities', activities)
	}

	private void nodeWithEnv(CipaNode node, Closure body) {
		script.node(node.nodeLabel) {
			script.echo('[CIPActivities] On host: ' + determineHostname())
			def workspace = script.env.WORKSPACE
			script.echo("[CIPActivities] workspace: ${workspace}")

			def envVars = []
			def pathEntries = []
			def configFiles = []

			for (tool in tools) {
				def toolHome = script.tool(name: tool.name, type: tool.type)
				script.echo("[CIPActivities] Tool ${tool.name}: ${toolHome}")
				if (tool.dedicatedEnvVar) {
					envVars.add("${tool.dedicatedEnvVar}=${toolHome}")
				}
				if (tool.addToPathWithSuffix) {
					pathEntries.add("${toolHome}${tool.addToPathWithSuffix}")
				}
				if (tool.is(toolMvn)) {
					def mvnRepo = determineMvnRepo()
					script.echo("[CIPActivities] mvnRepo: ${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_REPO}=${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_NODE_OPTIONS}=${toolMvn.options}")
				}
				for (configFileEnvVar in tool.configFileEnvVars) {
					configFiles.add(script.configFile(fileId: configFileEnvVar.value, variable: configFileEnvVar.key))
				}
			}

			envVars.add('PATH+=' + pathEntries.join(':'))

			script.withEnv(envVars) {
				script.configFileProvider(configFiles) {
					body()
				}
			}
		}
	}

	/**
	 * Obtain first non-null value from params followed by env (params access will be prefixed with P_).
	 * If required and both are null throw an exception otherwise return null.
	 */
	@NonCPS
	def obtainValueFromParamsOrEnv(String name, boolean required = true) {
		// P_ prefix needed otherwise params overwrite env
		def value = script.params['P_' + name] ?: script.env.getEnvironment()[name] ?: null
		if (value || !required) {
			return value
		}
		throw new RuntimeException("${name} is neither in env nor in params")
	}

	String determineHostname() {
		String hostnameRaw = script.sh(returnStdout: true, script: 'hostname')
		return hostnameRaw.trim()
	}

	String determineMvnRepo() {
		String workspace = script.env.WORKSPACE
		return workspace + '/.repo'
	}

	/**
	 * Determine SVN URL of current working directory.
	 */
	String determineSvnUrlOfCwd() {
		String svnRev = script.sh(returnStdout: true, script: 'svn info | awk \'/^URL/{print $2}\'')
		return svnRev
	}

	/**
	 * Determine SVN Revision of current working directory.
	 */
	String determineSvnRevOfCwd() {
		String svnRev = script.sh(returnStdout: true, script: 'svn info | awk \'/^Revision/{print $2}\'')
		return svnRev
	}

	String determineProjectVersionOfCwd() {
		return mvn(['org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate'], [], ['-N', '-Dexpression=project.version', '| grep -v \'\\[INFO\\]\' | tail -n 1 | tr -d \'\\r\\n\''], [], true)
	}

	String mvn(
			List<String> goals,
			List<String> profiles = [],
			List<String> arguments = [],
			List<String> options = [],
			boolean returnStdout = false) {
		def allArguments = ['-B', '-V', '-e']
		if (script.env[ENV_VAR___MVN_SETTINGS]) {
			allArguments.add('-s "${' + ENV_VAR___MVN_SETTINGS + '}"')
		}
		if (script.env[ENV_VAR___MVN_TOOLCHAINS]) {
			allArguments.add('--global-toolchains "${' + ENV_VAR___MVN_TOOLCHAINS + '}"')
		}
		allArguments.add('-Dmaven.repo.local="${' + ENV_VAR___MVN_REPO + '}"')
		if (!profiles.isEmpty()) {
			allArguments.add('-P' + profiles.join(','))
		}
		allArguments.addAll(goals)
		allArguments.addAll(arguments)

		def allArgumentsString = allArguments.isEmpty() ? '' : allArguments.join(' ')

		def allOptions = options.collect()
		allOptions.add('-Dmaven.multiModuleProjectDirectory="${' + ENV_VAR___MVN_HOME + '}"')
		allOptions.add('${' + ENV_VAR___MVN_NODE_OPTIONS + '}')
		def allOptionsString = allOptions.join(' ')

		script.withEnv(["MAVEN_OPTS=${allOptionsString}"]) {
			script.sh(script: 'echo "MAVEN_OPTS=${MAVEN_OPTS}"')
			return script.sh(script: "mvn ${allArgumentsString}", returnStdout: returnStdout)
		}
	}

	void cleanUpMvnRepo() {
		mvn(['org.codehaus.mojo:build-helper-maven-plugin:1.7:remove-project-artifact'])
	}

}
