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
import de.hasait.cipa.Script
import groovy.json.JsonSlurper

/**
 * Obtain more env variables from job description and parent folder descriptions.
 */
class CipaPrepareEnv implements CipaInit, CipaPrepare, Serializable {

	private static final String ENV_BEGIN_MARKER = 'vvv additionalEnv.json vvv'
	private static final String ENV_END_MARKER = '^^^ additionalEnv.json ^^^'

	private Script script
	private def rawScript

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(Script.class)
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

		List<String> descriptions = collectDescriptions()
		List<List<Object>> envAssignments = extractEnvAssignments(descriptions)

		for (envAssignment in envAssignments) {
			script.echo("Adding ${envAssignment[0]} to env with value: ${envAssignment[1]}")
			rawScript.env[(String) envAssignment[0]] = envAssignment[1]
		}
	}

	@NonCPS
	private List<String> collectDescriptions() {
		List<String> descriptions = new ArrayList<>()
		// start with Job
		def current = rawScript.currentBuild.rawBuild.parent
		while (current != null && current.hasProperty('description')) {
			def description = current.description
			if (description instanceof String) {
				descriptions.add(description)
			}
			current = current.hasProperty('parent') ? current.parent : null
		}
		return descriptions
	}

	@NonCPS
	private List<List<Object>> extractEnvAssignments(List<String> descriptions) {
		List<List<Object>> envAssignments = new ArrayList<>()

		for (description in descriptions.reverse()) {
			int ioBeginOfStartKey = description.indexOf(ENV_BEGIN_MARKER)
			if (ioBeginOfStartKey >= 0) {
				int ioAfterStartKey = ioBeginOfStartKey + ENV_BEGIN_MARKER.length()
				int ioAfterEndKey = description.lastIndexOf(ENV_END_MARKER)
				if (ioAfterStartKey < ioAfterEndKey) {
					String additionalEnvJSON = description.substring(ioAfterStartKey, ioAfterEndKey)
					def additionalEnv = new JsonSlurper().parseText(additionalEnvJSON)
					if (additionalEnv instanceof Map) {
						Map additionalEnvMap = additionalEnv
						for (additionalEnvEntry in additionalEnvMap) {
							if (additionalEnvEntry.key instanceof String) {
								envAssignments.add([additionalEnvEntry.key, additionalEnvEntry.value])
							}
						}
					}
				}
			}
		}

		return envAssignments
	}

}
