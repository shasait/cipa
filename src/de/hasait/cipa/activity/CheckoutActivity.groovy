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
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class CheckoutActivity extends AbstractCipaActivity implements CipaActivityWithStage {

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
		super(cipa)

		this.name = name
		this.withStage = withStage

		this.config = config
		cipa.addBean(config)

		this.checkedOutFiles = cipa.newFileResourceWithState(node, relDir ?: config.id + 'Files', 'CheckedOut')
		addRunProvides(checkedOutFiles)
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
			def result = script.checkout(config, forcedScmBranch)
			scmUrl = result.scmUrl
			scmRef = result.scmRef
			scmResolvedBranch = result.scmResolvedBranch
			scmRev = result.scmRev
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
