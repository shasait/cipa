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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.CipaNode
import de.hasait.cipa.nodehandler.AbstractCipaNodeHandler

/**
 *
 */
class CipaContainerImageNodeHandler extends AbstractCipaNodeHandler {

	static final int NODE_ORDER = CipaWorkspaceNodeHandler.NODE_ORDER + 2

	boolean forwardDockerSock = true

	CipaContainerImageNodeHandler(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	void handleNode(CipaNode node, Closure<?> next) {
		if (node.containerImageCoords) {
			List<String> containerArgs = new ArrayList<>()
			if (forwardDockerSock) {
				containerArgs.add("-v '/var/run/docker.sock:/var/run/docker.sock'")
			}
			containerArgs.addAll(node.containerArgs)
			rawScript.docker.image(node.containerImageCoords).inside(containerArgs.join(' ')) {
				next.call()
			}
		} else {
			next.call()
		}
	}

	@Override
	@NonCPS
	int getHandleNodeOrder() {
		return NODE_ORDER
	}

}
