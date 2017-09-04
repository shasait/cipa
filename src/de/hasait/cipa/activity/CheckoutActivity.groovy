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

	private final String prefix
	private final CipaResourceWithState<CipaFileResource> coResource

	private Script script
	private def rawScript

	private String scmUrl
	private String scmCredentialsId

	private Set<String> scmExcludeUsers = new LinkedHashSet<>()

	private String scmRev

	CheckoutActivity(String prefix, CipaResourceWithState<CipaFileResource> coResource) {
		this.prefix = prefix
		this.coResource = coResource
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(Script.class)
		rawScript = script.rawScript
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		container.addStringParameter(prefix + PARAM___SCM_URL, '', prefix + '-SCM-URL for checkout')
		container.addStringParameter(prefix + PARAM___SCM_CREDENTIALS_ID, '', prefix + '-SCM-Credentials needed for checkout')
	}

	@Override
	void processParameters(JobParameterValues values) {
		scmUrl = values.retrieveRequiredValue(prefix + PARAM___SCM_URL)
		scmCredentialsId = values.retrieveOptionalValue(prefix + PARAM___SCM_CREDENTIALS_ID, '')
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

	@Override
	void prepareNode() {
		// nop
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequires() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunProvides() {
		return [coResource]
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return coResource.resource.node
	}

	@Override
	@NonCPS
	String getDescription() {
		return prefix + '-Checkout'
	}

	@Override
	void run() {
		checkout()
	}

	@NonCPS
	private String buildExcludeUsersValue() {
		return scmExcludeUsers.join('\n')
	}

	private void checkout() {
		script.dir(coResource.resource.relDir) {
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

			script.echo("${prefix}-scmUrl = ${scmUrl}")
			script.echo("${prefix}-scmRev = ${scmRev}")
		}
	}

}
