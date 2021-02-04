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
import de.hasait.cipa.CipaWorkspaceProvider
import de.hasait.cipa.nodehandler.AbstractCipaNodeHandler

/**
 *
 */
class CipaWorkspaceNodeHandler extends AbstractCipaNodeHandler {

	CipaWorkspaceNodeHandler(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	void handleNode(CipaNode node, Closure<?> next) {
		CipaWorkspaceProvider workspaceProvider = cipa.findBean(CipaWorkspaceProvider.class, true)
		if (workspaceProvider == null) {
			echoWorkspace(next)
		} else {
			String wsPath = workspaceProvider.determineWorkspacePath()
			rawScript.ws(wsPath) {
				echoWorkspace(next)
			}
		}
	}

	private void echoWorkspace(Closure<?> next) {
		String workspace = rawScript.env.WORKSPACE
		script.echo("[CIPA] workspace: ${workspace}")
		next.call()
	}

	@Override
	@NonCPS
	int getHandleNodeOrder() {
		return -30000000
	}

}
