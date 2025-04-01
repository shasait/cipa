/*
 * Copyright (C) 2025 by Sebastian Hasait (sebastian at hasait dot de)
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
import de.hasait.cipa.jobprops.JobParameterContainer
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobParameterValues

/**
 * TODO move to package scm.
 */
class CheckoutConfiguration implements JobParameterContribution, Serializable {

	static final String SBT_TRUNK = 'trunk'
	static final String SBT_BRANCH = 'branch:'
	static final String SBT_TAG = 'tag:'
	static final String SBT_REV = 'rev:'
	static final String SBT_BRANCH_FROM_FOLDER = 'branch-from-folder'
	static final String SBT_NONE = 'none'
	static final String SBT_SCM = 'scm'

	static final String PARAM___SCM_URL = '_SCM_URL'
	static final String PARAM___SCM_CREDENTIALS_ID = '_SCM_CREDENTIALS_ID'
	static final String PARAM___SCM_BRANCH = '_SCM_BRANCH'
	static final String PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX = '_SCM_BFF_PREFIX'
	static final String PARAM___PRE_CHECKOUT_SYNC_REPO_DIR = '_PRE_CHECKOUT_SYNC_REPO_DIR'

	final String id
	final String idUpperCase
	List<String> subFolders = []

	boolean dry
	boolean params = true
	List<String> selectedParams = []
	boolean includeInPolling = true
	boolean includeInChangelog = true
	int shallowDepth = 0
	String preCheckoutSyncRepoDir
	String referenceRepoDir

	String scmUrl
	String scmCredentialsId
	String scmBranch
	String scmBffPrefix

	Set<String> pollingExcludedUsers = new LinkedHashSet<>()
	String pollingExcludedMessagePattern

	CheckoutConfiguration(String id, String subFolder = null) {
		this.id = id
		this.idUpperCase = id.toUpperCase()

		if (subFolder) {
			addSubFolder(subFolder)
		}
	}

	/**
	 * Do not really checkout anything; listeners are called nevertheless.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration enableDry() {
		dry = true
		return this
	}

	/**
	 * Do not contribute params; just read values from environment.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration disableParams() {
		params = false
		return this
	}

	/**
	 * If params is true, single params can be removed here; if params is false, single params can be added here.
	 * @param param One of the PARAM-constants here.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration selectedParam(String param) {
		selectedParams.add(param)
		return this
	}

	/**
	 * Add subfolder to checkout.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration addSubFolder(String subFolder) {
		subFolders.add(subFolder)
		return this
	}

	/**
	 * Do not include in SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration excludeFromPolling() {
		includeInPolling = false
		return this
	}

	/**
	 * Do not include in changelog.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration excludeFromChangelog() {
		includeInChangelog = false
		return this
	}

	/**
	 * @param users Users excluded from SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration excludeUser(String... users) {
		pollingExcludedUsers.addAll(users)
		return this
	}

	/**
	 * @param messagePattern Commits with message matching messagePattern will be ignored for SCM polling.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration excludeMessage(String messagePattern) {
		pollingExcludedMessagePattern = messagePattern
		return this
	}

	/**
	 * @param shallowDepth 0 = complete history (default); 1 = only latest; >1 for limited history
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration withShallowDepth(int shallowDepth) {
		this.shallowDepth = shallowDepth
		return this
	}

	/**
	 * @param preCheckoutSyncRepoDir Directory to copy if current dir is empty before doing any checkout.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration withPreCheckoutSyncRepoDir(String preCheckoutSyncRepoDir) {
		this.preCheckoutSyncRepoDir = preCheckoutSyncRepoDir
		return this
	}

	/**
	 * @param referenceRepoDir Directory of optional repository used to save time for cloning.
	 * @return this
	 */
	@NonCPS
	CheckoutConfiguration withReferenceRepo(String referenceRepoDir) {
		this.referenceRepoDir = referenceRepoDir
		return this
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		if (params == !selectedParams.contains(PARAM___SCM_URL)) {
			container.addStringParameter(idUpperCase + PARAM___SCM_URL, '', "${id}-SCM-URL for checkout (Git if ending in .git, otherwise SVN)")
		}
		if (params == !selectedParams.contains(PARAM___SCM_CREDENTIALS_ID)) {
			container.addStringParameter(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '', "${id}-SCM-Credentials needed for checkout")
		}
		if (params == !selectedParams.contains(PARAM___SCM_BRANCH)) {
			container.addStringParameter(idUpperCase + PARAM___SCM_BRANCH, '', "${id}-SCM-Branch for checkout (${SBT_TRUNK};${SBT_BRANCH}<i>name</i>;${SBT_TAG}<i>name</i>;${SBT_BRANCH_FROM_FOLDER};${SBT_REV}<i>revision</i>;${SBT_NONE})")
		}
		if (params == !selectedParams.contains(PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX)) {
			container.addStringParameter(idUpperCase + PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX, '', "${id}-SCM-Branch-Prefix if ${SBT_BRANCH_FROM_FOLDER} is used, otherwise has no effect")
		}
		if (params == !selectedParams.contains(PARAM___PRE_CHECKOUT_SYNC_REPO_DIR)) {
			container.addStringParameter(idUpperCase + PARAM___PRE_CHECKOUT_SYNC_REPO_DIR, '', "${id}-PreCheckoutSyncRepoDir - sync this cached repo before checkout")
		}
	}

	@Override
	void processParameters(JobParameterValues values) {
		scmUrl = values.retrieveOptionalStringParameterValue(idUpperCase + PARAM___SCM_URL, null)
		scmCredentialsId = values.retrieveOptionalStringParameterValue(idUpperCase + PARAM___SCM_CREDENTIALS_ID, null)
		scmBranch = values.retrieveOptionalStringParameterValue(idUpperCase + PARAM___SCM_BRANCH, SBT_NONE)
		scmBffPrefix = values.retrieveOptionalStringParameterValue(idUpperCase + PARAM___SCM_BRANCH_FROM_FOLDER_PREFIX, '')
		preCheckoutSyncRepoDir = values.retrieveOptionalStringParameterValue(idUpperCase + PARAM___PRE_CHECKOUT_SYNC_REPO_DIR, null)

		if (!(scmBranch == SBT_TRUNK || scmBranch.startsWith(SBT_BRANCH) || scmBranch.startsWith(SBT_TAG) || scmBranch == SBT_BRANCH_FROM_FOLDER || scmBranch.startsWith(SBT_REV) || scmBranch == SBT_NONE)) {
			throw new RuntimeException("Parameter ${idUpperCase + PARAM___SCM_BRANCH} invalid: ${scmBranch}")
		}
	}

	@NonCPS
	String buildExcludeUsersValue() {
		return pollingExcludedUsers.join('\n')
	}

	@Override
	@NonCPS
	String toString() {
		return "CheckoutConfiguration ${id}"
	}

}
