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

package de.hasait.cipa

import com.cloudbees.groovy.cps.NonCPS

/**
 *
 */
class CipaTool implements Serializable {

	private final Map<String, String> configFileEnvVars = new HashMap<>()

	private String name
	private String type
	private String dedicatedEnvVar
	private String addToPathWithSuffix
	private String options

	@NonCPS
	String getName() {
		return name
	}

	@NonCPS
	void setName(String name) {
		this.name = name
	}

	@NonCPS
	String getType() {
		return type
	}

	@NonCPS
	void setType(String type) {
		this.type = type
	}

	@NonCPS
	String getDedicatedEnvVar() {
		return dedicatedEnvVar
	}

	@NonCPS
	void setDedicatedEnvVar(String dedicatedEnvVar) {
		this.dedicatedEnvVar = dedicatedEnvVar
	}

	@NonCPS
	String getAddToPathWithSuffix() {
		return addToPathWithSuffix
	}

	@NonCPS
	void setAddToPathWithSuffix(String addToPathWithSuffix) {
		this.addToPathWithSuffix = addToPathWithSuffix
	}

	@NonCPS
	void addConfigFileEnvVar(String envVar, String configFileId) {
		configFileEnvVars.put(envVar, configFileId)
	}

	@NonCPS
	Map<String, String> getConfigFileEnvVars() {
		return configFileEnvVars
	}

	@NonCPS
	String getOptions() {
		return options
	}

	@NonCPS
	void setOptions(final String options) {
		this.options = options
	}

}
