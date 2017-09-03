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
import de.hasait.cipa.CipaBeanContainer
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaPrepare
import de.hasait.cipa.Script
import groovy.json.JsonSlurper

/**
 * Obtain more env variables from job description and parent folder descriptions.
 */
class CipaPrepareEnv implements CipaInit, CipaPrepare, Serializable {

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
		script.echo('populateEnvFromJobDescription')

		String jobDescriptionEnvStartKey = 'vvv additionalEnv.json vvv'
		String jobDescriptionEnvEndKey = '^^^ additionalEnv.json ^^^'

		List<String> descriptions = new ArrayList<>()
		def current = rawScript.currentBuild.rawBuild.parent
		while (current != null && current.hasProperty('description')) {
			def description = current.description
			if (description instanceof String) {
				descriptions.add(description)
			}
			current = current.hasProperty('parent') ? current.parent : null
		}

		for (description in descriptions.reverse()) {
			int ioBeginOfStartKey = description.indexOf(jobDescriptionEnvStartKey)
			if (ioBeginOfStartKey >= 0) {
				int ioAfterStartKey = ioBeginOfStartKey + jobDescriptionEnvStartKey.length()
				int ioAfterEndKey = description.lastIndexOf(jobDescriptionEnvEndKey)
				if (ioAfterStartKey < ioAfterEndKey) {
					String additionalEnvJSON = description.substring(ioAfterStartKey, ioAfterEndKey)
					script.echo('additionalEnv.json:')
					script.echo(additionalEnvJSON)
					def additionalEnv = new JsonSlurper().parseText(additionalEnvJSON)
					if (additionalEnv instanceof Map) {
						for (additionalEnvEntry in additionalEnv) {
							script.echo("Adding ${additionalEnvEntry.key} to env with value: ${additionalEnvEntry.value}")
							rawScript.env[additionalEnvEntry.key] = additionalEnvEntry.value
						}
					}
				}
			}
		}
	}

}
