/*
 * Copyright (C) 2021 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.resource

import com.cloudbees.groovy.cps.NonCPS

/**
 *
 */
class CipaResourceWithState<R extends CipaResource> implements Serializable {

	private final R resource
	private final String state

	CipaResourceWithState(R resource, String state) {
		if (resource == null) {
			throw new IllegalArgumentException('resource is null')
		}
		if (!state) {
			throw new IllegalArgumentException('state is null or empty')
		}

		this.resource = resource
		this.state = state
	}

	@NonCPS
	R getResource() {
		return resource
	}

	@NonCPS
	String getState() {
		return state
	}

	@Override
	@NonCPS
	String toString() {
		return "${resource} in state [${state}]"
	}

	@NonCPS
	CipaResourceWithState<R> newState(String state) {
		CipaResourceWithState<R> newResourceWithState = new CipaResourceWithState<R>(resource, state)
		return newResourceWithState
	}

}
