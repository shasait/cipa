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

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.CipaNode

/**
 *
 */
class CipaFileResource implements CipaResource, Serializable {

	private final CipaNode node
	private final String relDir

	CipaFileResource(CipaNode node, String relDir) {
		if (!node) {
			throw new IllegalArgumentException('node is null')
		}
		if (!relDir || relDir.length() == 0) {
			throw new IllegalArgumentException('relDir is null or empty')
		}

		this.node = node
		this.relDir = relDir
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return node
	}

	@NonCPS
	String getRelDir() {
		return relDir
	}

	@Override
	@NonCPS
	String toString() {
		return "Files[${relDir}] on ${node}"
	}

}
