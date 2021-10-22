/*
 * Copyright (C) 2021 by Sebastian Hasait (sebastian at hasait dot de)
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
import com.google.common.collect.ImmutableSet
import de.hasait.cipa.activity.CheckoutConfiguration
import de.hasait.cipa.activity.scm.ScmUrlTransformer
import de.hasait.cipa.tool.MavenExecution
import groovy.json.JsonSlurper
import hudson.FilePath
import hudson.model.Job
import hudson.model.Run
import hudson.remoting.VirtualChannel
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

/**
 * Wrapper for pipeline script allowing access to well known steps.
 */
class PScript implements Serializable {

	static final Set<String> FIND_FILES_INCLUDES_DEFAULT = ImmutableSet.of()
	static final Set<String> FIND_FILES_EXCLUDES_DEFAULT = ImmutableSet.of()
	static final boolean FIND_FILES_USE_DEFAULT_EXCLUDES_DEFAULT = true
	static final boolean FIND_FILES_ALLOW_EMPTY_DEFAULT = false

	static final Set<String> ARCHIVE_INCLUDES_DEFAULT = FIND_FILES_INCLUDES_DEFAULT
	static final Set<String> ARCHIVE_EXCLUDES_DEFAULT = FIND_FILES_EXCLUDES_DEFAULT
	static final boolean ARCHIVE_USE_DEFAULT_EXCLUDES_DEFAULT = FIND_FILES_USE_DEFAULT_EXCLUDES_DEFAULT
	static final boolean ARCHIVE_ALLOW_EMPTY_DEFAULT = FIND_FILES_ALLOW_EMPTY_DEFAULT
	static final boolean ARCHIVE_CASE_SENSITIVE_DEFAULT = true
	static final boolean ARCHIVE_FINGERPRINT_DEFAULT = false
	static final boolean ARCHIVE_ONLY_IF_SUCCESSFUL_DEFAULT = false

	static final Set<String> STASH_INCLUDES_DEFAULT = FIND_FILES_INCLUDES_DEFAULT
	static final Set<String> STASH_EXCLUDES_DEFAULT = FIND_FILES_EXCLUDES_DEFAULT
	static final boolean STASH_USE_DEFAULT_EXCLUDES_DEFAULT = FIND_FILES_USE_DEFAULT_EXCLUDES_DEFAULT
	static final boolean STASH_ALLOW_EMPTY_DEFAULT = FIND_FILES_ALLOW_EMPTY_DEFAULT

	@Deprecated
	static final String MVN_LOG = MavenExecution.MVN_LOG_FILE
	@Deprecated
	static final String MVN_REPO_RELDIR = MavenExecution.MVN_DEFAULT_REPO_RELDIR

	def rawScript

	PScript(rawScript) {
		this.rawScript = rawScript
	}

	@NonCPS
	static List<String> getLog(RunWrapper runWrapper, int maxLines) {
		Run rawBuild = runWrapper.rawBuild
		List<String> lines = rawBuild.getLog(maxLines)
		return lines
	}

	/**
	 * Wrapper for step archiveArtifacts with stash like parameters.
	 * @deprecated Prefer{@link de.hasait.cipa.activity.CipaActivityRunContext#archiveFiles(java.util.Set, java.util.Set, boolean, boolean)}.
	 */
	@Deprecated
	void archiveFiles(Set<String> includes = ARCHIVE_INCLUDES_DEFAULT, Set<String> excludes = ARCHIVE_EXCLUDES_DEFAULT, boolean useDefaultExcludes = ARCHIVE_USE_DEFAULT_EXCLUDES_DEFAULT, boolean allowEmpty = ARCHIVE_ALLOW_EMPTY_DEFAULT, boolean caseSensitive = ARCHIVE_CASE_SENSITIVE_DEFAULT, boolean fingerprint = ARCHIVE_FINGERPRINT_DEFAULT, boolean onlyIfSuccessful = ARCHIVE_ONLY_IF_SUCCESSFUL_DEFAULT) {
		rawScript.archiveArtifacts(allowEmptyArchive: allowEmpty, artifacts: includes?.join(','), caseSensitive: caseSensitive, defaultExcludes: useDefaultExcludes, excludes: excludes?.join(','), fingerprint: fingerprint, onlyIfSuccessful: onlyIfSuccessful)
	}

	/**
	 * @deprecated Prefer{@link de.hasait.cipa.activity.CipaActivityRunContext#archiveFiles(java.util.Set, java.util.Set, boolean, boolean)}.
	 */
	@Deprecated
	void archiveArtifacts(String artifacts, String excludes = null, boolean allowEmptyArchive = ARCHIVE_ALLOW_EMPTY_DEFAULT, boolean fingerprint = ARCHIVE_FINGERPRINT_DEFAULT, boolean onlyIfSuccessful = ARCHIVE_ONLY_IF_SUCCESSFUL_DEFAULT, boolean defaultExcludes = ARCHIVE_USE_DEFAULT_EXCLUDES_DEFAULT, boolean caseSensitive = ARCHIVE_CASE_SENSITIVE_DEFAULT) {
		rawScript.archiveArtifacts(allowEmptyArchive: allowEmptyArchive, artifacts: artifacts, caseSensitive: caseSensitive, defaultExcludes: defaultExcludes, excludes: excludes, fingerprint: fingerprint, onlyIfSuccessful: onlyIfSuccessful)
	}

	RunWrapper build(String job, List parameters = [], int embedLogMaxLines = 100) {
		RunWrapper runWrapper = rawScript.build(job: job, parameters: parameters, propagate: false)
		List<String> lines = getLog(runWrapper, embedLogMaxLines)
		echo("Build ${runWrapper.absoluteUrl}\nLast lines:\n${lines.join('\n')}")
		if (runWrapper.result == 'FAILURE') {
			throw new RuntimeException("Build ${runWrapper.absoluteUrl} failed!")
		}
		return runWrapper
	}

	CheckoutResult checkout(CheckoutConfiguration config, String forcedScmBranch = null, ScmUrlTransformer scmUrlTransformer = null) {
		String scmUrl
		String scmRef
		String scmResolvedBranch
		String scmRev

		scmUrl = scmUrlTransformer != null ? scmUrlTransformer.transformScmUrl(config.scmUrl) : config.scmUrl
		String scmBranch = forcedScmBranch ?: config.scmBranch

		if (!scmUrl) {
			if (!config.dry) {
				// If it fails here first check if the config without scmUrl is intended.
				rawScript.checkout rawScript.scm
				if (fileExists('.git')) {
					// Git
					scmResolvedBranch = determineGitBranchOfCwd()
					scmRev = determineGitRevOfCwd()
				} else {
					// Subversion
					scmUrl = determineSvnUrlOfCwd()
					scmRev = determineSvnRevOfCwd()
					if (scmUrl.endsWith('/trunk')) {
						scmResolvedBranch = 'trunk'
					} else {
						int ioBranches = scmUrl.lastIndexOf('/branches/')
						if (ioBranches > 0) {
							scmResolvedBranch = scmUrl.substring(ioBranches + '/branches/'.length())
						}
					}
				}
			}
		} else if (scmUrl.endsWith('.git')) {
			// Git
			scmRef = '*/master'
			if (scmBranch == CheckoutConfiguration.SBT_TRUNK) {
				scmResolvedBranch = 'master'
				scmRef = 'refs/heads/master'
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_BRANCH)) {
				scmResolvedBranch = scmBranch.substring(CheckoutConfiguration.SBT_BRANCH.length())
				scmRef = 'refs/heads/' + scmResolvedBranch
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_TAG)) {
				scmRef = 'refs/tags/' + scmBranch.substring(CheckoutConfiguration.SBT_TAG.length())
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_REV)) {
				scmRef = scmBranch.substring(CheckoutConfiguration.SBT_REV.length())
			} else if (scmBranch == CheckoutConfiguration.SBT_BRANCH_FROM_FOLDER) {
				String folderName = currentRawBuild.parent.parent.name
				if (folderName == 'trunk' || folderName == 'master') {
					scmResolvedBranch = 'master'
				} else {
					scmResolvedBranch = config.scmBffPrefix + folderName
				}
				scmRef = 'refs/heads/' + scmResolvedBranch
			}
			if (!config.dry) {
				List extensions = []
				extensions.add([$class: 'CleanCheckout'])
				if (!config.pollingExcludedUsers.empty) {
					extensions.add([$class: 'UserExclusion', excludedUsers: config.buildExcludeUsersValue()])
				}
				if (config.pollingExcludedMessagePattern) {
					extensions.add([$class: 'MessageExclusion', excludedMessage: config.pollingExcludedMessagePattern])
				}
				if (config.subFolders) {
					List pathList = []
					for (String subFolder in config.subFolders) {
						pathList.add([path: subFolder])
					}
					extensions.add([$class: 'SparseCheckoutPaths', sparseCheckoutPaths: pathList])
				}
				if (config.shallowDepth >= 1) {
					extensions.add([$class: 'CloneOption', shallow: true, depth: config.shallowDepth])
				}
				rawScript.checkout(
						changelog: config.includeInChangelog,
						poll: config.includeInPolling,
						scm: [
								$class                           : 'GitSCM',
								branches                         : [[name: scmRef]],
								doGenerateSubmoduleConfigurations: false,
								extensions                       : extensions,
								submoduleCfg                     : [],
								userRemoteConfigs                : [[credentialsId: config.scmCredentialsId, url: scmUrl]]
						])

				scmRev = determineGitRevOfCwd()
			}
		} else {
			// Subversion
			String subPath
			if (scmBranch == CheckoutConfiguration.SBT_TRUNK) {
				scmResolvedBranch = 'trunk'
				subPath = '/trunk'
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_BRANCH)) {
				scmResolvedBranch = scmBranch.substring(CheckoutConfiguration.SBT_BRANCH.length())
				subPath = '/branches/' + scmResolvedBranch
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_TAG)) {
				subPath = '/tags/' + scmBranch.substring(CheckoutConfiguration.SBT_TAG.length())
			} else if (scmBranch.startsWith(CheckoutConfiguration.SBT_REV)) {
				throw new RuntimeException("Not implemented yet")
			} else if (scmBranch == CheckoutConfiguration.SBT_BRANCH_FROM_FOLDER) {
				String folderName = currentRawBuild.parent.parent.name
				if (folderName == 'trunk') {
					scmResolvedBranch = 'trunk'
					subPath = '/trunk'
				} else {
					scmResolvedBranch = config.scmBffPrefix + folderName
					subPath = '/branches/' + scmResolvedBranch
				}
			}
			if (subPath) {
				scmUrl += subPath
			}
			int subFoldersSize = config.subFolders.size()
			if (subFoldersSize > 1) {
				throw new RuntimeException("Subversion cannot handle multiple subfolders")
			}
			if (subFoldersSize == 1) {
				scmUrl += '/' + config.subFolders.get(0)
			}

			if (!config.dry) {
				rawScript.checkout(
						changelog: config.includeInChangelog,
						poll: config.includeInPolling,
						scm: [
								$class                : 'SubversionSCM',
								additionalCredentials : [],
								excludedCommitMessages: config.pollingExcludedMessagePattern ?: '',
								excludedRegions       : '',
								excludedRevprop       : '',
								excludedUsers         : config.buildExcludeUsersValue(),
								filterChangelog       : false,
								ignoreDirPropChanges  : false,
								includedRegions       : '',
								locations             : [[
																 credentialsId        : config.scmCredentialsId,
																 depthOption          : 'infinity',
																 ignoreExternalsOption: true,
																 local                : '.',
																 remote               : scmUrl
														 ]],
								workspaceUpdater      : [$class: 'UpdateWithCleanUpdater']
						])

				scmUrl = determineSvnUrlOfCwd()
				scmRev = determineSvnRevOfCwd()
			}
		}

		echo("${config.id}: scmUrl = ${scmUrl}; scmRef = ${scmRef}; scmResolvedBranch = ${scmResolvedBranch}; scmRev = ${scmRev}")

		return new CheckoutResult(scmUrl: scmUrl, scmRef: scmRef, scmResolvedBranch: scmResolvedBranch, scmRev: scmRev)
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

	void deleteDir() {
		rawScript.deleteDir()
	}

	String determineHostname() {
		String hostnameRaw = rawScript.sh(returnStdout: true, script: 'hostname')
		return hostnameRaw.trim()
	}

	String determineMvnRepo() {
		String workspace = rawScript.env.WORKSPACE
		return workspace + '/' + MVN_REPO_RELDIR
	}

	@NonCPS
	Map<String, Object> determineParametersFromDescriptionValues(Job<?, ?> job = currentRawBuild.parent) {
		List<String> descriptions = collectDescriptions(job)
		// TODO move additionalEnv to CipaPrepareEnv after projects migrated
		return parseJsonBlocks(descriptions, 'parameters', 'additionalEnv')
	}

	/**
	 * Determine SVN URL of current working directory.
	 */
	String determineSvnUrlOfCwd() {
		String svnUrl = sh('svn info | awk \'/^URL/{print $2}\'', true)
		return svnUrl.trim()
	}

	/**
	 * Determine SVN Revision of current working directory.
	 */
	String determineSvnRevOfCwd() {
		String svnRev = sh('svn info | awk \'/^Revision/{print $2}\'', true)
		return svnRev.trim()
	}

	/**
	 * Determine Git Branch of current working directory.
	 */
	String determineGitBranchOfCwd() {
		String gitRef = sh('git symbolic-ref HEAD', true).trim()
		String prefix = 'refs/heads/'
		if (gitRef.startsWith(prefix)) {
			return gitRef.substring(prefix.length())
		}
		return gitRef
	}

	/**
	 * Determine Git Revision of current working directory.
	 */
	String determineGitRevOfCwd() {
		String gitRev = sh('git rev-parse HEAD', true)
		return gitRev.trim()
	}

	public <V> V dir(String dirname, Closure<V> body) {
		rawScript.dir(dirname, body)
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

	boolean fileExists(String relativePath) {
		return rawScript.fileExists(relativePath)
	}

	Set<String> findFiles(Set<String> includes = FIND_FILES_INCLUDES_DEFAULT, Set<String> excludes = FIND_FILES_EXCLUDES_DEFAULT, boolean useDefaultExcludes = FIND_FILES_USE_DEFAULT_EXCLUDES_DEFAULT, boolean allowEmpty = FIND_FILES_ALLOW_EMPTY_DEFAULT) {
		String nodeName = rawScript.env['NODE_NAME']
		String path = pwd()
		return findFilesInternal(nodeName, path, includes, excludes, useDefaultExcludes, allowEmpty)
	}

	@NonCPS
	private Set<String> findFilesInternal(String nodeName, String path, Set<String> includes, Set<String> excludes, boolean useDefaultExcludes, boolean allowEmpty) {
		Set<String> result = new LinkedHashSet<>()

		FilePath base = createFilePath(nodeName, path)
		int relPathBaseIndex = base.remote.length() + 1

		FilePath[] matchList = base.list(includes.join(','), excludes.join(','), useDefaultExcludes)
		for (FilePath match in matchList) {
			if (!match.directory) {
				result.add(match.remote.substring(relPathBaseIndex))
			}
		}

		if (result.empty && !allowEmpty) {
			throw new IllegalArgumentException("No files found: nodeName=${nodeName}, path=${path}, includes=${includes}, excludes=${excludes}, useDefaultExcludes=${useDefaultExcludes}")
		}

		return result
	}

	@NonCPS
	private FilePath createFilePath(String nodeName, String path) {
		if (nodeName == null) {
			throw new IllegalArgumentException('nodeName is null')
		}

		if (nodeName == 'master') {
			return new FilePath(new File(path))
		}

		VirtualChannel channel = jenkins.getNode(nodeName).getChannel()

		return new FilePath(channel, path)
	}

	@NonCPS
	int getCurrentBuildNumber() {
		return rawScript.currentBuild.number
	}

	@NonCPS
	Run<?, ?> getCurrentRawBuild() {
		return rawScript.currentBuild.rawBuild
	}

	Object getCustomBuildProperty(String key) {
		return rawScript.getCustomBuildProperty(key: key)
	}

	@NonCPS
	def getItemByFullName(String fullName) {
		return jenkins.getItemByFullName(fullName)
	}

	// not Jenkins return type to allow testing
	@NonCPS
	def getJenkins() {
		def current = currentRawBuild
		while (current.hasProperty('parent') && current.parent != null) {
			current = current.parent
		}
		return current
	}

	@NonCPS
	MavenExecution mvn() {
		return new MavenExecution(rawScript)
	}

	/**
	 *
	 * @deprecated Prefer{@link #mvn()}.
	 */
	@Deprecated
	String mvn(
			List<String> goals,
			List<String> profiles = [],
			List<String> arguments = [],
			List<String> options = [],
			boolean returnStdout = false,
			List<String> stdoutFilters = MavenExecution.MVN_DEFAULT_STDOUT_FILTERS
	) {
		return mvn().addGoals(goals).addProfiles(profiles).addArguments(arguments).addOptions(options).replaceStdoutFilters(stdoutFilters).execute(returnStdout)
	}

	String pwd() {
		return rawScript.pwd()
	}

	String readFile(String filepath, String encoding = 'UTF-8') {
		return rawScript.readFile(encoding: encoding, file: filepath)
	}

	def readJsonFile(String filepath) {
		return rawScript.readJSON(file: filepath)
	}

	def readJsonText(String text) {
		return rawScript.readJSON(text: text)
	}

	void setCustomBuildProperty(String key, Object value) {
		rawScript.setCustomBuildProperty(key: key, value: value)
	}

	String sh(String script, boolean returnStdout = false) {
		return rawScript.sh(script: script, returnStdout: returnStdout)
	}

	String shAsUser(String username, String script, boolean returnStdout = false) {
		String escapedScript = script.replace('\\', '\\\\').replace('"', '\\"')
		return sh('echo "' + escapedScript + '" | ssh -T -o "BatchMode yes" ' + username + '@localhost', returnStdout)
	}

	void sleep(int seconds) {
		rawScript.sleep(seconds)
	}

	public <V> V stage(String name, Closure<V> body) {
		rawScript.stage(name, body)
	}

	/**
	 * Wrapper for step stash.
	 * @deprecated Prefer{@link de.hasait.cipa.activity.CipaActivityRunContext#stash(java.lang.String)}.
	 */
	@Deprecated
	void stash(String id, Set<String> includes = STASH_INCLUDES_DEFAULT, Set<String> excludes = STASH_EXCLUDES_DEFAULT, boolean useDefaultExcludes = STASH_USE_DEFAULT_EXCLUDES_DEFAULT, boolean allowEmpty = STASH_ALLOW_EMPTY_DEFAULT) {
		rawScript.stash(name: id, includes: includes?.join(','), excludes: excludes?.join(','), useDefaultExcludes: useDefaultExcludes, allowEmpty: allowEmpty)
	}

	public <V> V timeout(int timeoutInMinutes, Closure<V> body) {
		rawScript.timeout(timeoutInMinutes, body)
	}

	void writeFile(String filepath, String content, String encoding = 'UTF-8') {
		rawScript.writeFile(encoding: encoding, file: filepath, text: content)
	}

	/**
	 * Wrapper for step unstash.
	 * @deprecated Prefer{@link de.hasait.cipa.activity.CipaActivityRunContext#unstash(java.lang.String)}.
	 */
	@Deprecated
	void unstash(String id) {
		rawScript.unstash(id)
	}

	/**
	 * Use folder {@link MavenExecution#MVN_DEFAULT_REPO_RELDIR} in current working directory as mvn repo (will be created automatically).
	 * @deprecated Prefer{@link MavenExecution#withPrivateRepo(java.lang.String)}.
	 */
	@Deprecated
	public <V> V withPrivateMvnRepo(Closure<V> body) {
		def envVars = []
		String mvnRepo = pwd() + '/' + MavenExecution.MVN_DEFAULT_REPO_RELDIR
		envVars.add("${Cipa.ENV_VAR___MVN_REPO}=${mvnRepo}")
		rawScript.withEnv(envVars) {
			body()
		}
	}

	@NonCPS
	Map<String, Object> parseJsonBlocks(List<String> descriptions, String... blockIds) {

		Map<String, Object> result = new LinkedHashMap<>()

		for (description in descriptions.reverse()) {
			for (String blockId in blockIds) {
				String blockBeginMarker = "vvv ${blockId}.json vvv"
				String blockEndMarker = "^^^ ${blockId}.json ^^^"
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
		}

		return result
	}

	String determineCurrentFlowNodeUrl() {
		def uuid = UUID.randomUUID().toString()
		echo uuid
		determineCurrentFlowNodeUrlInternal(uuid)
	}

	@NonCPS
	private String determineCurrentFlowNodeUrlInternal(String uuid) {
		def fgi = new FlowGraphWalker(currentRawBuild.execution).iterator()
		while (fgi.hasNext()) {
			def fn = fgi.next()
			if (fn.typeFunctionName == 'echo') {
				def argsAction = fn.getAction(org.jenkinsci.plugins.workflow.actions.ArgumentsAction)
				if (argsAction?.getArgumentValue('message') == uuid) {
					return fn.url
				}
			}
		}
		return null
	}

	/**
	 * Provided string will be placed within single quotes (') and escaped accordingly.
	 * @param string The string to encapsulate in single quotes, e.g. a path to a file
	 * @return The string ready for use in <code>script.sh(...)</code>.
	 */
	@NonCPS
	static String encapsulateStringInSingleQuotesForShell(String string) {
		String escaped = string
		escaped = escaped.replace('\'', '\'"\'"\'')
		return "'${escaped}'"
	}

	static class CheckoutResult implements Serializable {
		String scmUrl
		String scmRef
		String scmResolvedBranch
		String scmRev
	}

}
