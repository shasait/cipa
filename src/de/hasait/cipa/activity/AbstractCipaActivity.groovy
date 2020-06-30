/*
 * Copyright (C) 2018 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.CipaNode
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState
import org.apache.commons.lang.StringUtils

/**
 *
 */
abstract class AbstractCipaActivity extends AbstractCipaBean implements CipaActivity, CipaActivityWithCleanup {

	private Set<CipaResourceWithState<?>> requiresRead = []
	private Set<CipaResourceWithState<?>> requiresWrite = []
	private Set<CipaResourceWithState<?>> provides = []
	private Set<CipaNode> nodesFromResources = []
	String groupIdentifier = null

	AbstractCipaActivity(rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	@NonCPS
	String getName() {
		return StringUtils.removeEnd(getClass().getSimpleName(), 'Activity')
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		def iterator = nodesFromResources.iterator()
		if (iterator.hasNext()) {
			CipaNode result = iterator.next()
			if (!iterator.hasNext()) {
				return result
			}
		}
		throw new RuntimeException('Cannot determine unique node from resources - override getNode')
	}

	@NonCPS
	protected final <R extends CipaResource> void addRunRequiresRead(CipaResourceWithState<R> resourceWithState) {
		requiresRead.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
		if (resourceWithState.resource.node != null) {
			nodesFromResources.add(resourceWithState.resource.node)
		}
	}

	@NonCPS
	protected final <R extends CipaResource> void addRunRequiresWrite(CipaResourceWithState<R> resourceWithState) {
		requiresWrite.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
		if (resourceWithState.resource.node != null) {
			nodesFromResources.add(resourceWithState.resource.node)
		}
	}

	@NonCPS
	protected final <R extends CipaResource> void addRunProvides(CipaResourceWithState<R> resourceWithState) {
		provides.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
		if (resourceWithState.resource.node != null) {
			nodesFromResources.add(resourceWithState.resource.node)
		}
	}

	@NonCPS
	protected final <R extends CipaResource> CipaResourceWithState<R> modifiesResource(CipaResourceWithState<R> inResourceWithState, String newState) {
		addRunRequiresWrite(inResourceWithState)
		CipaResourceWithState<R> outResourceWithState = inResourceWithState.newState(newState)
		addRunProvides(outResourceWithState)
		return outResourceWithState
	}

	@Override
	@NonCPS
	final Set<CipaResourceWithState<?>> getRunRequiresRead() {
		return requiresRead
	}

	@Override
	@NonCPS
	final Set<CipaResourceWithState<?>> getRunRequiresWrite() {
		return requiresWrite
	}

	@Override
	@NonCPS
	final Set<CipaResourceWithState<?>> getRunProvides() {
		return provides
	}

	@Override
	void prepareNode() {
		// empty default implementation
	}

	@Override
	void cleanupNode() {
		// empty default implementation
	}

}
