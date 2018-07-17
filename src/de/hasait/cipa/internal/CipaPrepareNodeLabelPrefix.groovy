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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.jobprops.JobParameterContainer
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobParameterValues

/**
 *
 */
class CipaPrepareNodeLabelPrefix implements JobParameterContribution, Serializable {

	private static final String PARAM___NODE_LABEL_PREFIX = 'NODE_LABEL_PREFIX'

	private boolean params = true

	private String nodeLabelPrefix

	/**
	 * Do not contribute params; just read values from environment.
	 * @return this
	 */
	@NonCPS
	CipaPrepareNodeLabelPrefix disableParams() {
		params = false
		return this
	}

	@Override
	void contributeParameters(JobParameterContainer container) {
		if (params) {
			container.addStringParameter(PARAM___NODE_LABEL_PREFIX, '', 'Prefix for node labels')
		}
	}

	@Override
	void processParameters(JobParameterValues values) {
		nodeLabelPrefix = values.retrieveOptionalValue(PARAM___NODE_LABEL_PREFIX, '')
	}

	String getNodeLabelPrefix() {
		return nodeLabelPrefix
	}

}
