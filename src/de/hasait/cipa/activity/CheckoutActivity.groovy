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

	private String scmUrl
	private String scmCredentialsId
	private String scmBranch

	private Set<String> scmExcludeUsers = new LinkedHashSet<>()

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
		container.addStringParameter(idUpperCase + PARAM___SCM_URL, '', "${id}-SCM-URL for checkout (Git if ending in .git, otherwise SVN)")
		container.addStringParameter(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '', "${id}-SCM-Credentials needed for checkout")
		container.addStringParameter(idUpperCase + PARAM___SCM_BRANCH, '', "${id}-SCM-Branch for checkout (${SBT_TRUNK};${SBT_BRANCH}<i>name</i>;${SBT_TAG}<i>name</i>;${SBT_BRANCH_FROM_FOLDER};${SBT_NONE})")
	}

	@Override
	void processParameters(JobParameterValues values) {
		scmUrl = values.retrieveRequiredValue(idUpperCase + PARAM___SCM_URL)
		scmCredentialsId = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '')
		scmBranch = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_BRANCH, SBT_NONE)

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
	void runActivity() {
		checkout()
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
				String branch = '*/master'
				if (scmBranch == SBT_TRUNK) {
					branch = 'refs/heads/master'
				} else if (scmBranch.startsWith(SBT_BRANCH)) {
					branch += 'refs/heads/' + scmBranch.substring(SBT_BRANCH.length())
				} else if (scmBranch.startsWith(SBT_TAG)) {
					branch += 'refs/tags/' + scmBranch.substring(SBT_TAG.length())
				} else if (scmBranch == SBT_BRANCH_FROM_FOLDER) {
					String folderName = script.currentRawBuild.parent.parent.name
					branch += 'refs/heads/' + folderName
				}

				List extensions = []
				extensions.add([$class: 'CleanBeforeCheckout'])
				if (!scmExcludeUsers.empty) {
					extensions.add([$class: 'UserExclusion', excludedUsers: buildExcludeUsersValue()])
				}
				if (subFolder) {
					extensions.add([$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: subFolder]]])
				}
				rawScript.checkout([
						$class                           : 'GitSCM',
						branches                         : [[name: branch]],
						doGenerateSubmoduleConfigurations: false,
						extensions                       : extensions,
						submoduleCfg                     : [],
						userRemoteConfigs                : [[credentialsId: scmCredentialsId, url: scmUrl]]
				])

				scmRev = script.determineGitRevOfCwd()
			} else {
				// Subversion
				if (scmBranch == SBT_TRUNK) {
					scmUrl += '/trunk'
				} else if (scmBranch.startsWith(SBT_BRANCH)) {
					scmUrl += '/branches/' + scmBranch.substring(SBT_BRANCH.length())
				} else if (scmBranch.startsWith(SBT_TAG)) {
					scmUrl += '/tags/' + scmBranch.substring(SBT_TAG.length())
				} else if (scmBranch == SBT_BRANCH_FROM_FOLDER) {
					String folderName = script.currentRawBuild.parent.parent.name
					if (folderName == 'trunk') {
						scmUrl += '/trunk'
					} else {
						scmUrl += '/branches/' + folderName
					}
				}
				if (subFolder) {
					scmUrl += '/' + subFolder
				}

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

			script.echo("${id}-scmUrl = ${scmUrl}")
			script.echo("${id}-scmRev = ${scmRev}")
		}
	}

	@NonCPS
	String getCheckedOutScmUrl() {
		return scmUrl
	}

	@NonCPS
	String getCheckedOutScmRev() {
		return scmRev
	}

}
