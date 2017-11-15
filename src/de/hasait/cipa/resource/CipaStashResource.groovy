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
class CipaStashResource implements CipaResource, Serializable {

	private final String id
	private final String srcRelDir

	CipaStashResource(String id, String srcRelDir) {
		if (!id || id.length() == 0) {
			throw new IllegalArgumentException('id is null or empty')
		}
		this.id = id
		this.srcRelDir = srcRelDir
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return null
	}

	@NonCPS
	String getId() {
		return id
	}

	@NonCPS
	String getSrcRelDir() {
		return srcRelDir
	}

	@Override
	@NonCPS
	String toString() {
		return "Stash[${id}]"
	}

}
