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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.CipaNode
import de.hasait.cipa.NodeCleanup
import de.hasait.cipa.nodehandler.AbstractCipaNodeHandler

/**
 *
 */
class CipaCleanupNodeHandler extends AbstractCipaNodeHandler {

	CipaCleanupNodeHandler(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	void handleNode(CipaNode node, Closure<?> next) {
		next.call()

		// Will be invoked after all activities for each node
		List<NodeCleanup> cleanupResources = cipa.findBeansAsList(NodeCleanup.class)
		for (cleanResource in cleanupResources) {
			cleanResource.cleanupNode(node)
		}
	}

	@Override
	@NonCPS
	int getHandleNodeOrder() {
		return -10000000
	}

}
