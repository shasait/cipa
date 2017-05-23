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

	private final Map<String, String> _configFileEnvVars = new HashMap<>()

	private String _name
	private String _type
	private String _dedicatedEnvVar
	private String _addToPathWithSuffix

	String getName() {
		return _name
	}

	void setName(String name) {
		_name = name
	}

	String getType() {
		return _type
	}

	void setType(String type) {
		_type = type
	}

	String getDedicatedEnvVar() {
		return _dedicatedEnvVar
	}

	void setDedicatedEnvVar(String dedicatedEnvVar) {
		_dedicatedEnvVar = dedicatedEnvVar
	}

	String getAddToPathWithSuffix() {
		return _addToPathWithSuffix
	}

	void setAddToPathWithSuffix(String addToPathWithSuffix) {
		_addToPathWithSuffix = addToPathWithSuffix
	}

	void addConfigFileEnvVar(String envVar, String configFileId) {
		_configFileEnvVars.put(envVar, configFileId)
	}

	Map<String, String> getConfigFileEnvVars() {
		return Collections.unmodifiableMap(_configFileEnvVars)
	}

}
