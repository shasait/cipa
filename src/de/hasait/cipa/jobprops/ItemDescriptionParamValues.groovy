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

package de.hasait.cipa.jobprops

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.PScript
import hudson.model.Item
import jenkins.model.Jenkins

class ItemDescriptionParamValues implements CipaParamValueProvider, Serializable {

	private final PScript script
	private final def rawScript

	private final Map<String, Map<String, Object>> cache = [:]

	ItemDescriptionParamValues(PScript script) {
		this.script = script
		this.rawScript = script.rawScript
	}

	@Override
	@NonCPS
	Object getParamValueForCurrentRun(String name) {
		return null
	}

	@Override
	@NonCPS
	Object getParamValueForItem(String name, Item item) {
		String cacheKey = item.fullName
		Map<String, Object> parsedJsonBlock
		if (cache.containsKey(cacheKey)) {
			parsedJsonBlock = cache.get(cacheKey)
		} else {
			if (item.hasProperty('description')) {
				Object description = item.description
				if (description instanceof String) {
					parsedJsonBlock = script.parseJsonBlocks([description], 'parameters', 'additionalEnv')
				} else {
					parsedJsonBlock = null
				}
			} else {
				parsedJsonBlock = null
			}
			cache.put(cacheKey, parsedJsonBlock)
		}
		return parsedJsonBlock?.get(name)
	}

	@Override
	@NonCPS
	Object getParamValueForJenkins(String name, Jenkins jenkins) {
		return null
	}

	@Override
	@NonCPS
	int getParamValueProviderOrder() {
		return 0
	}

}
