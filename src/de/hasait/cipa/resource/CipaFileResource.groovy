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

package de.hasait.cipa.resource

import de.hasait.cipa.CipaNode
import de.hasait.cipa.resource.CipaResource

/**
 *
 */
class CipaFileResource implements CipaResource, Serializable {

	private final CipaNode node
	private final String relDir
	private final String state

	CipaFileResource(CipaNode node, String relDir, String state) {
		if (!node) {
			throw new IllegalArgumentException('node is null')
		}
		if (!relDir || relDir.length() == 0) {
			throw new IllegalArgumentException('relDir is null or empty')
		}

		if (!state || state.length() == 0) {
			throw new IllegalArgumentException('state is null or empty')
		}

		this.node = node
		this.relDir = relDir
		this.state = state
	}

	@Override
	CipaNode getNode() {
		return node
	}

	@Override
	String getDescription() {
		return "Files [${relDir}] on [${node}] in state [${state}]"
	}

	String getRelDir() {
		return relDir
	}

	String toString() {
		return description
	}

}
