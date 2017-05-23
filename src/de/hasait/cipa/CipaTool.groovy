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

	String getName() {
		return name
	}

	void setName(String name) {
		this.name = name
	}

	String getType() {
		return type
	}

	void setType(String type) {
		this.type = type
	}

	String getDedicatedEnvVar() {
		return dedicatedEnvVar
	}

	void setDedicatedEnvVar(String dedicatedEnvVar) {
		this.dedicatedEnvVar = dedicatedEnvVar
	}

	String getAddToPathWithSuffix() {
		return addToPathWithSuffix
	}

	void setAddToPathWithSuffix(String addToPathWithSuffix) {
		this.addToPathWithSuffix = addToPathWithSuffix
	}

	void addConfigFileEnvVar(String envVar, String configFileId) {
		configFileEnvVars.put(envVar, configFileId)
	}

	Map<String, String> getConfigFileEnvVars() {
		return Collections.unmodifiableMap(configFileEnvVars)
	}

	String getOptions() {
		return options
	}

	void setOptions(final String options) {
		this.options = options
	}

}
