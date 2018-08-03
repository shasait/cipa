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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaNode
import de.hasait.cipa.PScript
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class CheckoutActivity implements CipaActivity, CipaActivityWithStage, Serializable {

	private final Cipa cipa
	private final PScript script
	private final def rawScript
	private final String name
	private final boolean withStage
	private final CheckoutConfiguration config

	private final CipaResourceWithState<CipaFileResource> checkedOutFiles

	private String scmUrl
	private String scmRef
	private String scmResolvedBranch
	private String scmRev
	private String forcedScmBranch

	CheckoutActivity(Cipa cipa, String name, CipaNode node, CheckoutConfiguration config, boolean withStage = true, String relDir = null) {
		this.cipa = cipa
		this.script = cipa.findBean(PScript.class)
		this.rawScript = script.rawScript
		this.name = name
		this.withStage = withStage
		this.config = config
		this.checkedOutFiles = cipa.newFileResourceWithState(node, relDir ?: config.id + 'Files', 'CheckedOut')

		cipa.addBean(this)
		cipa.addBean(config)
		cipa.addBean(checkedOutFiles.resource)
		cipa.addBean(checkedOutFiles)
	}

	CheckoutActivity(Cipa cipa, String name, String id, CipaNode node, String subFolder = null, boolean withStage = true, String relDir = null) {
		this(cipa, name, node, new CheckoutConfiguration(id, subFolder), withStage, relDir)
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	@NonCPS
	boolean isWithStage() {
		return withStage
	}

	/**
	 * Do not really checkout anything; listeners are called nevertheless.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity enableDry() {
		config.enableDry()
		return this
	}

	/**
	 * Do not contribute params; just read values from environment.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity disableParams() {
		config.disableParams()
		return this
	}

	/**
	 * Do not include in SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity excludeFromPolling() {
		config.excludeFromPolling()
		return this
	}

	/**
	 * Do not include in changelog.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity excludeFromChangelog() {
		config.excludeFromChangelog()
		return this
	}

	/**
	 * @param users Users excluded from SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity excludeUser(String... users) {
		config.excludeUser(users)
		return this
	}

	/**
	 * @param messagePattern Commits with message matching messagePattern will be ignored for SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity excludeMessage(String messagePattern) {
		config.excludeMessage(messagePattern)
		return this
	}

	/**
	 * @param forcedScmBranch Use this branch expression and not from config. 
	 * @return this
	 */
	@NonCPS
	CheckoutActivity withScmBranchExpression(String forcedScmBranch) {
		this.forcedScmBranch = forcedScmBranch
		return this
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> getProvidedCheckedOutFiles() {
		return checkedOutFiles
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresRead() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresWrite() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunProvides() {
		return [checkedOutFiles]
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return checkedOutFiles.resource.node
	}

	@Override
	void prepareNode() {
		// nop
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		checkout()

		List<CheckoutDoneListener> checkoutDoneListeners = cipa.findBeansAsList(CheckoutDoneListener.class)
		for (CheckoutDoneListener checkoutDoneListener in checkoutDoneListeners) {
			checkoutDoneListener.checkoutDone(this, runContext)
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Checkout ${config.id} into ${checkedOutFiles}"
	}

	private void checkout() {
		script.dir(checkedOutFiles.resource.path) {
			scmUrl = config.scmUrl
			String scmBranch = forcedScmBranch ?: config.scmBranch

			if (scmUrl.endsWith('.git')) {
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
					String folderName = script.currentRawBuild.parent.parent.name
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
							pathList.add([path: '/' + subFolder])
						}
						extensions.add([$class: 'SparseCheckoutPaths', sparseCheckoutPaths: pathList])
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

					scmRev = script.determineGitRevOfCwd()
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
					String folderName = script.currentRawBuild.parent.parent.name
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

					scmUrl = script.determineSvnUrlOfCwd()
					scmRev = script.determineSvnRevOfCwd()
				}
			}

			script.echo("${config.id}-scmUrl = ${scmUrl}")
			script.echo("${config.id}-scmRef = ${scmRef}")
			script.echo("${config.id}-scmRev = ${scmRev}")
		}
	}

	@NonCPS
	String getCheckOutId() {
		return config.id
	}

	@NonCPS
	String getCheckOutIdUpperCase() {
		return config.idUpperCase
	}

	@NonCPS
	String getCheckedOutScmUrl() {
		return scmUrl
	}

	@NonCPS
	String getCheckedOutScmRef() {
		return scmRef
	}

	@NonCPS
	String getCheckedOutScmRev() {
		return scmRev
	}

	@NonCPS
	String getResolvedBranch() {
		return scmResolvedBranch
	}

}
