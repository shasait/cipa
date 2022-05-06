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
import com.cloudbees.hudson.plugins.folder.AbstractFolder
import de.hasait.cipa.PScript
import groovy.json.JsonSlurper
import hudson.model.Item
import jenkins.model.Jenkins
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.jenkinsci.plugins.configfiles.folder.FolderConfigFileProperty

class ManagedFilesParamValues implements CipaParamValueProvider, Serializable {

	static final String FILE_ID = 'parameters.json'

	private final PScript script
	private final def rawScript

	private final Map<String, Map<String, Object>> cache = [:]

	ManagedFilesParamValues(PScript script) {
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
		return retrieveValue(name, 'item/' + item.fullName, item)
	}

	@Override
	@NonCPS
	Object getParamValueForJenkins(String name, Jenkins jenkins) {
		return retrieveValue(name, 'jenkins', jenkins)
	}

	@Override
	@NonCPS
	int getParamValueProviderOrder() {
		return 1000
	}

	@NonCPS
	private Object retrieveValue(String name, String cacheKey, Object itemOrJenkins) {
		Map<String, Object> parsedJsonBlock
		if (cache.containsKey(cacheKey)) {
			parsedJsonBlock = cache.get(cacheKey)
		} else {
			String content
			if (itemOrJenkins instanceof AbstractFolder) {
				FolderConfigFileProperty folderConfigFileProperty = itemOrJenkins.properties.get(FolderConfigFileProperty.class)
				if (folderConfigFileProperty != null) {
					content = folderConfigFileProperty.getById(FILE_ID)?.content
				} else {
					content = null
				}
			} else if (itemOrJenkins instanceof Jenkins) {
				content = GlobalConfigFiles.get().getById(FILE_ID)?.content
			} else {
				content = null
			}
			if (content != null) {
				Object parsedObject = new JsonSlurper().parseText(content)
				if (parsedObject instanceof Map) {
					parsedJsonBlock = parsedObject
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

}
