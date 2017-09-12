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
import de.hasait.cipa.Script
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class CheckoutActivity implements CipaInit, JobParameterContribution, CipaActivity, Serializable {

	private static final String PARAM___SCM_URL = '_SCM_URL'
	private static final String PARAM___SCM_CREDENTIALS_ID = '_SCM_CREDENTIALS_ID'

	private final Cipa cipa
	private final String name
	private final String id
	private final String idUpperCase
	private final CipaResourceWithState<CipaFileResource> checkedOutFiles

	private Script script
	private def rawScript

	private String scmUrl
	private String scmCredentialsId

	private Set<String> scmExcludeUsers = new LinkedHashSet<>()

	private String scmRev

	CheckoutActivity(Cipa cipa, String name, String id, CipaNode node) {
		this.cipa = cipa
		this.name = name
		this.id = id

		this.idUpperCase = id.toUpperCase()
		this.checkedOutFiles = cipa.newFileResourceWithState(node, "${id}Files", 'CheckedOut')

		cipa.addBean(this)
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

		script = cipa.findBean(Script.class)
		rawScript = script.rawScript
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		container.addStringParameter(idUpperCase + PARAM___SCM_URL, '', "${id}-SCM-URL for checkout")
		container.addStringParameter(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '', "${id}-SCM-Credentials needed for checkout")
	}

	@Override
	void processParameters(JobParameterValues values) {
		scmUrl = values.retrieveRequiredValue(idUpperCase + PARAM___SCM_URL)
		scmCredentialsId = values.retrieveOptionalValue(idUpperCase + PARAM___SCM_CREDENTIALS_ID, '')
	}

	@Override
	@NonCPS
	String getName() {
		return name
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
				List extensions = []
				extensions.add([$class: 'CleanBeforeCheckout'])
				if (!scmExcludeUsers.empty) {
					extensions.add([$class: 'UserExclusion', excludedUsers: buildExcludeUsersValue()])
				}
				rawScript.checkout([
						$class                           : 'GitSCM',
						branches                         : [[name: '*/master']],
						doGenerateSubmoduleConfigurations: false,
						extensions                       : extensions,
						submoduleCfg                     : [],
						userRemoteConfigs                : [[credentialsId: scmCredentialsId, url: scmUrl]]
				])

				scmRev = script.determineGitRevOfCwd()
			} else {
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

}
