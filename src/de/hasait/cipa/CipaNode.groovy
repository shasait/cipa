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
class CipaNode implements Serializable {

	private final String label
	private final boolean applyPrefix

	/**
	 * Hostname - only available while executing of activities.
	 */
	String runtimeHostname

	CipaNode(String label, boolean applyPrefix = true) {
		if (!label) {
			throw new IllegalArgumentException('label is null')
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

	@Override
	@NonCPS
	String toString() {
		return "Node[${label}]"
	}

}
