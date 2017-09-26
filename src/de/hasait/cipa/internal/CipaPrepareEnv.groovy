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
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaPrepare
import de.hasait.cipa.PScript

/**
 * Obtain more env variables from job description and parent folder descriptions.
 */
class CipaPrepareEnv implements CipaInit, CipaPrepare, Serializable {

	private PScript script
	private def rawScript

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript
	}

	@Override
	@NonCPS
	int getPrepareCipaOrder() {
		return 1000
	}

	@Override
	void prepareCipa(Cipa cipa) {
		script.echo('Populating env from descriptions...')

		List<List<Object>> envAssignments = extractEnvAssignments()

		for (envAssignment in envAssignments) {
			script.echo("Adding ${envAssignment[0]} to env with value: ${envAssignment[1]}")
			rawScript.env[(String) envAssignment[0]] = envAssignment[1]
		}
	}

	@NonCPS
	private List<List<Object>> extractEnvAssignments() {
		List<List<Object>> envAssignments = new ArrayList<>()

		List<String> descriptions = script.collectDescriptions()
		Map<String, Object> additionalEnvMap = script.parseJsonBlocks(descriptions, 'additionalEnv')

		for (additionalEnvEntry in additionalEnvMap) {
			envAssignments.add([additionalEnvEntry.key, additionalEnvEntry.value])
		}

		return envAssignments
	}

}
