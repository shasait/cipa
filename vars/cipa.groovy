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

import de.hasait.cipa.CipaActivity
import de.hasait.cipa.CipaNode
import groovy.transform.Field

@Field
String _jdkVersion
@Field
String _mvnVersion
@Field
String _mvnSettingsFileId
@Field
String _mvnToolchainsFileId
@Field
String _mvnOptions

@Field
final List<CipaNode> _nodes = new ArrayList<>()
@Field
final List<CipaActivity> _activities = new ArrayList<>()

String getJdkVersion() {
	return _jdkVersion
}

void setJdkVersion(final String pJdkVersion) {
	_jdkVersion = pJdkVersion
}

String getMvnVersion() {
	return _mvnVersion
}

void setMvnVersion(final String pMvnVersion) {
	_mvnVersion = pMvnVersion
}

String getMvnSettingsFileId() {
	return _mvnSettingsFileId
}

void setMvnSettingsFileId(final String pMvnSettingsFileId) {
	_mvnSettingsFileId = pMvnSettingsFileId
}

String getMvnToolchainsFileId() {
	return _mvnToolchainsFileId
}

void setMvnToolchainsFileId(final String pMvnToolchainsFileId) {
	_mvnToolchainsFileId = pMvnToolchainsFileId
}

String getMvnOptions() {
	return _mvnOptions
}

void setMvnOptions(final String pMvnOptions) {
	_mvnOptions = pMvnOptions
}

CipaNode newNode(final String pNodeLabel) {
	CipaNode node = new CipaNode(pNodeLabel)
	_nodes.add(node)
	return node
}

CipaActivity newActivity(final CipaNode pNode, final String pDescription, Closure pBody) {
	CipaActivity activity = new CipaActivity(pNode, pDescription, pBody)
	_activities.add(activity)
	return activity
}

private Closure parallelNodeWithActivitiesBranch(final CipaNode pNode, final List<CipaActivity> pActivities) {
	return {
		def parallelActivitiesBranches = [:]
		for (int i = 0; i < pActivities.size(); i++) {
			CipaActivity activity = pActivities.get(i)
			parallelActivitiesBranches["${i}-${activity.description}"] = parallelActivityRunBranch(activity)
		}
		nodeWithEnv(pNode) {
			parallel(parallelActivitiesBranches)
		}
	}
}

private Closure parallelActivityRunBranch(final CipaActivity pActivity) {
	return {
		waitUntil() {
			pActivity.allDependenciesSucceeded()
		}
		pActivity.runActivity()
	}
}

void runActivities() {
	echo("[CIPActivities] Running...")

	if (!_jdkVersion) {
		throw new IllegalStateException('jdkVersion undefined')
	}
	if (!_mvnVersion) {
		throw new IllegalStateException('mvnVersion undefined')
	}

	def parallelNodeBranches = [:]
	for (int i = 0; i < _nodes.size(); i++) {
		CipaNode node = _nodes.get(i)
		List<CipaActivity> activities = new ArrayList<>()
		for (activity in _activities) {
			if (activity.node.is(node)) {
				activities.add(activity)
			}
		}
		parallelNodeBranches["${i}-${node.nodeLabel}"] = parallelNodeWithActivitiesBranch(node, activities)
	}

	stage('Pipeline') {
		parallel(parallelNodeBranches)
	}

	echo("[CIPActivities] Done")

	for (activity in _activities) {
		echo("[CIPActivities] Activity: ${activity.description}")
		echo("[CIPActivities]     ${activity.buildStateHistoryString()}")
	}
}

private void nodeWithEnv(final CipaNode pNode, final Closure pBody) {
	node(pNode.nodeLabel) {
		echo("[CIPActivities] On host: " + determineHostname())
		def workspace = env.WORKSPACE
		echo("[CIPActivities] workspace: ${workspace}")
		def javaHome = tool name: _jdkVersion, type: 'hudson.model.JDK'
		echo("[CIPActivities] javaHome: ${javaHome}")
		def mvnHome = tool name: _mvnVersion, type: 'hudson.tasks.Maven$MavenInstallation'
		echo("[CIPActivities] mvnHome: ${mvnHome}")
		def mvnRepo = determineMvnRepo()
		echo("[CIPActivities] mvnRepo: ${mvnRepo}")
		def nodeMvnOptions = _mvnOptions + ' -Dmaven.multiModuleProjectDirectory=' + mvnHome + ' -Dmaven.repo.local=' + mvnRepo
		withEnv(["JAVA_HOME=${javaHome}", "M2_HOME=${mvnHome}", "MAVEN_OPTS=${nodeMvnOptions}", 'PATH=$JAVA_HOME/bin:$M2_HOME/bin:$PATH']) {
			def configFiles = []
			if (_mvnSettingsFileId) {
				configFiles.add(configFile(fileId: _mvnSettingsFileId, variable: 'MVN_SETTINGS'))
			}
			if (_mvnToolchainsFileId) {
				configFiles.add(configFile(fileId: _mvnToolchainsFileId, variable: 'MVN_TOOLCHAINS'))
			}
			configFileProvider(configFiles) {
				pBody()
			}
		}
	}
}

/**
 * Obtain first non-null value from params followed by env (params access will be prefixed with P_).
 * If required and both are null throw an exception otherwise return null.
 */
def obtainValueFromParamsOrEnv(final String pName, final boolean pRequired = true) {
	// P_ prefix needed otherwise params overwrite env
	def value = params['P_' + pName] ?: env.getEnvironment()[pName] ?: null
	if (value || !pRequired) {
		return value
	}
	throw new RuntimeException("${pName} is neither in env nor in params")
}

String determineHostname() {
	String hostnameRaw = sh(returnStdout: true, script: 'hostname')
	return hostnameRaw.trim()
}

String determineMvnRepo() {
	String workspace = env.WORKSPACE
	return workspace + '/.repo'
}

/**
 * Determine SVN URL of current working directory.
 */
String determineSvnUrlOfCwd() {
	String svnRev = sh(returnStdout: true, script: 'svn info | awk \'/^URL/{print $2}\'')
	return svnRev
}

/**
 * Determine SVN Revision of current working directory.
 */
String determineSvnRevOfCwd() {
	String svnRev = sh(returnStdout: true, script: 'svn info | awk \'/^Revision/{print $2}\'')
	return svnRev
}

String determineProjectVersionOfCwd() {
	return mvn(goals: ['org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate'], arguments: ['-N', '-Dexpression=project.version', '| grep -v \'\\[INFO\\]\' | tr -d \'\\r\\n\''], returnStdout: true)
}

String mvn(
		final List<String> pGoals,
		final List<String> pProfiles = [],
		final List<String> pArguments = [],
		final List<String> pOptions = [],
		final boolean pReturnStdout = false) {
	def allArguments = ['-B', '-V', '-e']
	if (env['MAVEN_TOOLCHAINS']) {
		allArguments.add('--global-toolchains "${MAVEN_TOOLCHAINS}"')
	}
	if (!pProfiles.isEmpty()) {
		allArguments.add('-P' + pProfiles.join(','))
	}
	allArguments.addAll(pGoals)
	allArguments.addAll(pArguments)

	def allArgumentsString = allArguments.isEmpty() ? '' : allArguments.join(' ')

	def allOptions = pOptions.collect()
	allOptions.add('${MAVEN_OPTS}')
	def allOptionsString = allOptions.join(' ')

	withEnv(["MAVEN_OPTS=${allOptionsString}"]) {
		return sh(script: "mvn ${allArgumentsString}", returnStdout: pReturnStdout)
	}
}

void cleanUpMvnRepo() {
	mvn(['org.codehaus.mojo:build-helper-maven-plugin:1.7:remove-project-artifact'])
}
