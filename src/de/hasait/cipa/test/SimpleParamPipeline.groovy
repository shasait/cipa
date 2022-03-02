/*
 * Copyright (C) 2022 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.test

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.CipaNode
import de.hasait.cipa.activity.AbstractCipaActivity
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.jobprops.JobParameterContainer
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobParameterValues

class SimpleParamPipeline extends AbstractCipaActivity implements JobParameterContribution, Serializable {

	static final String PARAM___SOME_STRING = 'SOME_STRING'

	private final CipaNode node

	private String someString

	SimpleParamPipeline(rawScript) {
		super(rawScript)

		this.node = cipa.newNode('linux')

		cipa.addBean(this)
		cipa.disableNodeLabelPrefixParam()
	}

	void run() {
		cipa.run()
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return node
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		container.addStringParameter(PARAM___SOME_STRING, '', 'Some String parameter.')
	}

	@Override
	void processParameters(JobParameterValues values) {
		someString = values.retrieveRequiredStringParameterValue(PARAM___SOME_STRING)
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		script.echo(someString)
	}

}
