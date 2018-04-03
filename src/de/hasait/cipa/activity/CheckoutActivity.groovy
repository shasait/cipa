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
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaNode
import de.hasait.cipa.JobParameterContainer
import de.hasait.cipa.JobParameterContribution
import de.hasait.cipa.JobParameterValues
import de.hasait.cipa.PScript
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class CheckoutActivity implements CipaInit, JobParameterContribution, CipaActivity, CipaActivityWithStage, Serializable {

	private static final String PARAM___SCM_URL = '_SCM_URL'
	private static final String PARAM___SCM_CREDENTIALS_ID = '_SCM_CREDENTIALS_ID'
	private static final String PARAM___SCM_BRANCH = '_SCM_BRANCH'
	private static final String PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX = '_SCM_BFF_PREFIX'

	private static final String SBT_TRUNK = 'trunk'
	private static final String SBT_BRANCH = 'branch:'
	private static final String SBT_TAG = 'tag:'
	private static final String SBT_BRANCH_FROM_FOLDER = 'branch-from-folder'
	private static final String SBT_NONE = 'none'

	private final Cipa cipa
	private final String name
	private final boolean withStage
	private final String id
	private final String idUpperCase
	private final String subFolder

	private final CipaResourceWithState<CipaFileResource> checkedOutFiles

	private PScript script
	private def rawScript

	private boolean dry
	private boolean params = true

	private String scmUrl
	private String scmCredentialsId
	private String scmBranch
	private String scmBffPrefix

	private Set<String> scmExcludeUsers = new LinkedHashSet<>()

	private String scmResolvedBranch
	private String scmRef
	private String scmRev

	CheckoutActivity(Cipa cipa, String name, String id, CipaNode node, String subFolder = null, boolean withStage = true) {
		this.cipa = cipa
		this.name = name
		this.withStage = withStage
		this.id = id
		this.idUpperCase = id.toUpperCase()
		this.subFolder = subFolder

		this.checkedOutFiles = cipa.newFileResourceWithState(node, "${id}Files", 'CheckedOut')

		cipa.addBean(this)
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
		dry = true
		return this
	}

	/**
	 * Do not contribute params; just read values from environment.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity disableParams() {
		params = false
		return this
	}

	/**
	 * @param users Users excluded from polling.
	 * @return this
	 */
	@NonCPS
	CheckoutActivity excludeUser(String... users) {
		scmExcludeUsers.addAll(users)
		return this
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> getProvidedCheckedOutFiles() {
		return checkedOutFiles
	}

	@Override
	void initCipa(Cipa cipa) {
		cipa.addBean(checkedOutFiles.resource)
		cipa.addBean(checkedOutFiles)

		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		if (params) {
			container.addStringParameter(idUpperCase + PARAM___SCM_URL, '', "${id}-SCM-URL for checkout (Git if ending in .git, otherwise SVN)")
			container.addStringParameter(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '', "${id}-SCM-Credentials needed for checkout")
			container.addStringParameter(idUpperCase + PARAM___SCM_BRANCH, '', "${id}-SCM-Branch for checkout (${SBT_TRUNK};${SBT_BRANCH}<i>name</i>;${SBT_TAG}<i>name</i>;${SBT_BRANCH_FROM_FOLDER};${SBT_NONE})")
			container.addStringParameter(idUpperCase + PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX, '', "${id}-SCM-Branch-Prefix if ${SBT_BRANCH_FROM_FOLDER} is used, otherwise has no effect")
		}
	}

	@Override
	void processParameters(JobParameterValues values) {
		scmUrl = values.retrieveRequiredValue(idUpperCase + PARAM___SCM_URL)
		scmCredentialsId = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '')
		scmBranch = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_BRANCH, SBT_NONE)
		scmBffPrefix = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX, '')

		if (!(scmBranch == SBT_TRUNK || scmBranch.startsWith(SBT_BRANCH) || scmBranch.startsWith(SBT_TAG) || scmBranch == SBT_BRANCH_FROM_FOLDER || scmBranch == SBT_NONE)) {
			throw new RuntimeException("Parameter ${idUpperCase + PARAM___SCM_BRANCH} invalid: ${scmBranch}")
		}
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
		return "Checkout ${id} into ${checkedOutFiles}"
	}

	@NonCPS
	private String buildExcludeUsersValue() {
		return scmExcludeUsers.join('\n')
	}

	private void checkout() {
		script.dir(checkedOutFiles.resource.path) {
			if (scmUrl.endsWith('.git')) {
				// Git
				scmRef = '*/master'
				if (scmBranch == SBT_TRUNK) {
					scmResolvedBranch = 'master'
					scmRef = 'refs/heads/master'
				} else if (scmBranch.startsWith(SBT_BRANCH)) {
					scmResolvedBranch = scmBranch.substring(SBT_BRANCH.length())
					scmRef = 'refs/heads/' + scmResolvedBranch
				} else if (scmBranch.startsWith(SBT_TAG)) {
					scmRef = 'refs/tags/' + scmBranch.substring(SBT_TAG.length())
				} else if (scmBranch == SBT_BRANCH_FROM_FOLDER) {
					String folderName = script.currentRawBuild.parent.parent.name
					if (folderName == 'trunk' || folderName == 'master') {
						scmResolvedBranch = 'master'
					} else {
						scmResolvedBranch = scmBffPrefix + folderName
					}
					scmRef = 'refs/heads/' + scmResolvedBranch
				}

				List extensions = []
				extensions.add([$class: 'CleanCheckout'])
				if (!scmExcludeUsers.empty) {
					extensions.add([$class: 'UserExclusion', excludedUsers: buildExcludeUsersValue()])
				}
				if (subFolder) {
					extensions.add([$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: '/' + subFolder]]])
				}
				if (!dry) {
					rawScript.checkout([
							$class                           : 'GitSCM',
							branches                         : [[name: scmRef]],
							doGenerateSubmoduleConfigurations: false,
							extensions                       : extensions,
							submoduleCfg                     : [],
							userRemoteConfigs                : [[credentialsId: scmCredentialsId, url: scmUrl]]
					])

					scmRev = script.determineGitRevOfCwd()
				}
			} else {
				// Subversion
				String subPath
				if (scmBranch == SBT_TRUNK) {
					scmResolvedBranch = 'trunk'
					subPath = '/trunk'
				} else if (scmBranch.startsWith(SBT_BRANCH)) {
					scmResolvedBranch = scmBranch.substring(SBT_BRANCH.length())
					subPath = '/branches/' + scmResolvedBranch
				} else if (scmBranch.startsWith(SBT_TAG)) {
					subPath = '/tags/' + scmBranch.substring(SBT_TAG.length())
				} else if (scmBranch == SBT_BRANCH_FROM_FOLDER) {
					String folderName = script.currentRawBuild.parent.parent.name
					if (folderName == 'trunk') {
						scmResolvedBranch = 'trunk'
						subPath = '/trunk'
					} else {
						scmResolvedBranch = scmBffPrefix + folderName
						subPath = '/branches/' + scmResolvedBranch
					}
				}
				if (subPath) {
					scmUrl += subPath
				}
				if (subFolder) {
					scmUrl += '/' + subFolder
				}
				if (!dry) {
					rawScript.checkout([
							$class                : 'SubversionSCM',
							additionalCredentials : [],
							excludedCommitMessages: '',
							excludedRegions       : '',
							excludedRevprop       : '',
							excludedUsers         : buildExcludeUsersValue(),
							filterChangelog       : false,
							ignoreDirPropChanges  : false,
							includedRegions       : '',
							locations             : [[
															 credentialsId        : scmCredentialsId,
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

			script.echo("${id}-scmUrl = ${scmUrl}")
			script.echo("${id}-scmRef = ${scmRef}")
			script.echo("${id}-scmRev = ${scmRev}")
		}
	}

	@NonCPS
	String getCheckOutId() {
		return id
	}

	@NonCPS
	String getCheckOutIdUpperCase() {
		return idUpperCase
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
