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

	private String scmRev

	CheckoutActivity(Cipa cipa, String name, CipaNode node, CheckoutConfiguration config, boolean withStage = true) {
		this.cipa = cipa
		this.script = cipa.findBean(PScript.class)
		this.rawScript = script.rawScript
		this.name = name
		this.withStage = withStage
		this.config = config
		this.checkedOutFiles = cipa.newFileResourceWithState(node, "${config.id}Files", 'CheckedOut')

		cipa.addBean(this)
		cipa.addBean(config)
		cipa.addBean(checkedOutFiles.resource)
		cipa.addBean(checkedOutFiles)
	}

	CheckoutActivity(Cipa cipa, String name, String id, CipaNode node, String subFolder = null, boolean withStage = true) {
		this(cipa, name, node, new CheckoutConfiguration(id, subFolder), withStage)
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
			if (config.scmUrl.endsWith('.git')) {
				// Git
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
				if (!config.dry) {
					rawScript.checkout(
							changelog: config.includeInChangelog,
							poll: config.includeInPolling,
							scm: [
									$class                           : 'GitSCM',
									branches                         : [[name: config.scmRef]],
									doGenerateSubmoduleConfigurations: false,
									extensions                       : extensions,
									submoduleCfg                     : [],
									userRemoteConfigs                : [[credentialsId: config.scmCredentialsId, url: config.scmUrl]]
							])

					scmRev = script.determineGitRevOfCwd()
				}
			} else {
				// Subversion
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
																	 remote               : config.scmUrl
															 ]],
									workspaceUpdater      : [$class: 'UpdateWithCleanUpdater']
							])

					config.scmUrl = script.determineSvnUrlOfCwd()
					scmRev = script.determineSvnRevOfCwd()
				}
			}

			script.echo("${config.id}-scmUrl = ${config.scmUrl}")
			script.echo("${config.id}-scmRef = ${config.scmRef}")
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
		return config.scmUrl
	}

	@NonCPS
	String getCheckedOutScmRef() {
		return config.scmRef
	}

	@NonCPS
	String getCheckedOutScmRev() {
		return scmRev
	}

	@NonCPS
	String getResolvedBranch() {
		return config.scmResolvedBranch
	}

}
