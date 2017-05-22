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
	def script
	private String jdkVersion
	private String mvnVersion
	private String mvnSettingsFileId
	private String mvnToolchainsFileId
	private String mvnOptions

	private final List<CipaNode> nodes = new ArrayList<>()
	private final List<CipaActivity> activities = new ArrayList<>()

	Cipa(script) {
		if (!script) {
			throw new IllegalArgumentException('script')
		}
		this.script = script;
	}

	String getJdkVersion() {
		return jdkVersion
	}

	void setJdkVersion(String jdkVersion) {
		this.jdkVersion = jdkVersion
	}

	String getMvnVersion() {
		return mvnVersion
	}

	void setMvnVersion(String mvnVersion) {
		this.mvnVersion = mvnVersion
	}

	String getMvnSettingsFileId() {
		return mvnSettingsFileId
	}

	void setMvnSettingsFileId(String mvnSettingsFileId) {
		this.mvnSettingsFileId = mvnSettingsFileId
	}

	String getMvnToolchainsFileId() {
		return mvnToolchainsFileId
	}

	void setMvnToolchainsFileId(String mvnToolchainsFileId) {
		this.mvnToolchainsFileId = mvnToolchainsFileId
	}

	String getMvnOptions() {
		return mvnOptions
	}

	void setMvnOptions(String mvnOptions) {
		this.mvnOptions = mvnOptions
	}

	CipaNode newNode(String nodeLabel) {
		CipaNode node = new CipaNode(nodeLabel)
		nodes.add(node)
		return node
	}

	CipaActivity newActivity(CipaNode node, String description, Closure body) {
		CipaActivity activity = new CipaActivity(node, description, body)
		activities.add(activity)
		return activity
	}

	private Closure parallelNodeWithActivitiesBranch(CipaNode node, List<CipaActivity> nodeActivities) {
		return {
			def parallelActivitiesBranches = [:]
			for (int i = 0; i < nodeActivities.size(); i++) {
				CipaActivity activity = nodeActivities.get(i)
				parallelActivitiesBranches["${i}-${activity.description}"] = parallelActivityRunBranch(activity)
			}
			nodeWithEnv(node) {
				script.parallel(parallelActivitiesBranches)
			}
		}
	}

	private Closure parallelActivityRunBranch(CipaActivity activity) {
		return {
			script.waitUntil() {
				activity.allDependenciesSucceeded()
			}
			activity.runActivity()
		}
	}

	void runActivities() {
		script.echo("[CIPActivities] Running...")

		if (!jdkVersion) {
			throw new IllegalStateException('jdkVersion undefined')
		}
		if (!mvnVersion) {
			throw new IllegalStateException('mvnVersion undefined')
		}

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
			script.parallel(parallelNodeBranches)
		}

		script.echo("[CIPActivities] Done")

		for (activity in activities) {
			script.echo("[CIPActivities] Activity: ${activity.description}")
			script.echo("[CIPActivities]     ${activity.buildStateHistoryString()}")
		}
	}

	private void nodeWithEnv(CipaNode node, Closure body) {
		script.node(node.nodeLabel) {
			script.echo("[CIPActivities] On host: " + determineHostname())
			def workspace = script.env.WORKSPACE
			script.echo("[CIPActivities] workspace: ${workspace}")
			def javaHome = script.tool(name: jdkVersion, type: 'hudson.model.JDK')
			script.echo("[CIPActivities] javaHome: ${javaHome}")
			def mvnHome = script.tool(name: mvnVersion, type: 'hudson.tasks.Maven$MavenInstallation')
			script.echo("[CIPActivities] mvnHome: ${mvnHome}")
			def mvnRepo = determineMvnRepo()
			script.echo("[CIPActivities] mvnRepo: ${mvnRepo}")
			def nodeMvnOptions = mvnOptions + ' -Dmaven.multiModuleProjectDirectory=' + mvnHome
			def nodePathElements = ['$JAVA_HOME/bin', '$M2_HOME/bin']
			if (node.initializer) {
				List<String> initializerPathElements = node.initializer()
				if (initializerPathElements) {
					nodePathElements.addAll(initializerPathElements)
				}
			}
			nodePathElements.add('$PATH')
			def pathElementsString = nodePathElements.join(':')
			script.withEnv(["JAVA_HOME=${javaHome}", "M2_HOME=${mvnHome}", "MVN_REPO=${mvnRepo}", "MVN_NODE_OPTS=${nodeMvnOptions}", 'PATH=' + pathElementsString]) {
				def configFiles = []
				if (mvnSettingsFileId) {
					configFiles.add(script.configFile(fileId: mvnSettingsFileId, variable: 'MVN_SETTINGS'))
				}
				if (mvnToolchainsFileId) {
					configFiles.add(script.configFile(fileId: mvnToolchainsFileId, variable: 'MVN_TOOLCHAINS'))
				}
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
		return mvn(goals: ['org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate'], arguments: ['-N', '-Dexpression=project.version', '| grep -v \'\\[INFO\\]\' | tr -d \'\\r\\n\''], returnStdout: true)
	}

	String mvn(
			List<String> goals,
			List<String> profiles = [],
			List<String> arguments = [],
			List<String> options = [],
			boolean returnStdout = false) {
		def allArguments = ['-B', '-V', '-e']
		if (script.env['MVN_SETTINGS']) {
			allArguments.add('-s "${MVN_SETTINGS}"')
		}
		if (script.env['MVN_TOOLCHAINS']) {
			allArguments.add('--global-toolchains "${MVN_TOOLCHAINS}"')
		}
		allArguments.add('-Dmaven.repo.local="${MVN_REPO}"')
		if (!profiles.isEmpty()) {
			allArguments.add('-P' + profiles.join(','))
		}
		allArguments.addAll(goals)
		allArguments.addAll(arguments)

		def allArgumentsString = allArguments.isEmpty() ? '' : allArguments.join(' ')

		def allOptions = options.collect()
		allOptions.add('${MVN_NODE_OPTS}')
		def allOptionsString = allOptions.join(' ')

		script.withEnv(["MAVEN_OPTS=${allOptionsString}"]) {
			return script.sh(script: "mvn ${allArgumentsString}", returnStdout: returnStdout)
		}
	}

	void cleanUpMvnRepo() {
		mvn(['org.codehaus.mojo:build-helper-maven-plugin:1.7:remove-project-artifact'])
	}

}
