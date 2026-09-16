/*
 * Copyright (C) 2026 by Sebastian Hasait (sebastian at hasait dot de)
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
class CipaNode implements Serializable {

	private final String label
	private final boolean applyPrefix

	/**
	 * E.g. https://public.ecr.aws
	 */
	private String containerRegistryUrl
	private String containerRegistryCredId
	/**
	 * E.g. docker/library/maven:3.9.16-eclipse-temurin-25-noble
	 */
	private String containerImageCoords
	private final List<String> containerArgs = []

	private final Map<String, String> configFileEnvVars = new HashMap<>()
	private final List<String> additionalEnvVars = []

	/**
	 * Hostname - only available while executing of activities.
	 */
	String runtimeHostname

	CipaNode(String label, boolean applyPrefix = true) {
		if (!label) {
			throw new IllegalArgumentException('label is null or empty')
		}

		this.label = label
		this.applyPrefix = applyPrefix
	}

	@NonCPS
	String getLabel() {
		return label
	}

	@NonCPS
	boolean isApplyPrefix() {
		return applyPrefix
	}

	@NonCPS
	String getContainerRegistryUrl() {
		return containerRegistryUrl
	}

	@NonCPS
	String getContainerRegistryCredId() {
		return containerRegistryCredId
	}

	@NonCPS
	String getContainerImageCoords() {
		return containerImageCoords
	}

	@NonCPS
	CipaNode withContainer(String registryUrl, String imageCoords, String credentialsId = null) {
		this.containerRegistryUrl = registryUrl
		this.containerImageCoords = imageCoords
		this.containerRegistryCredId = credentialsId
		return this
	}

	@NonCPS
	List<String> getContainerArgs() {
		return Collections.unmodifiableList(containerArgs)
	}

	@NonCPS
	void addContainerArgs(String... containerArgs) {
		this.containerArgs.addAll(containerArgs)
	}

	@NonCPS
	void removeAllContainerArgs() {
		containerArgs.clear()
	}

	List<String> getAdditionalEnvVars() {
		return Collections.unmodifiableList(additionalEnvVars)
	}

	@NonCPS
	void addAdditionalEnvVar(String envVar, String value) {
		additionalEnvVars.add(envVar + "=" + value)
	}

	@NonCPS
	void addAdditionalEnvVarSupplement(String envVar, String value) {
		additionalEnvVars.add(envVar + "+=" + value)
	}

	@NonCPS
	Map<String, String> getConfigFileEnvVars() {
		return Collections.unmodifiableMap(configFileEnvVars)
	}

	@NonCPS
	void addConfigFileEnvVar(String envVar, String configFileId) {
		configFileEnvVars.put(envVar, configFileId)
	}

	@Override
	@NonCPS
	String toString() {
		final StringBuffer sb = new StringBuffer("CipaNode{")
		sb.append("label='").append(label).append('\'')
		if (!applyPrefix) {
			sb.append(", applyPrefix=").append(applyPrefix)
		}
		if (containerRegistryUrl) {
			sb.append(", containerRegistryUrl='").append(containerRegistryUrl).append('\'')
		}
		if (containerRegistryCredId) {
			sb.append(", containerRegistryCredId='").append(containerRegistryCredId).append('\'')
		}
		if (containerImageCoords) {
			sb.append(", containerImageCoords='").append(containerImageCoords).append('\'')
		}
		sb.append('}')
		return sb.toString()
	}

}
