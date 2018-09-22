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
import de.hasait.cipa.Cipa
import de.hasait.cipa.resource.CipaResourceWithState

/**
 *
 */
abstract class AbstractCipaActivity extends AbstractCipaBean implements CipaActivity, CipaActivityWithCleanup {

	private Set<CipaResourceWithState<?>> requiresRead = []
	private Set<CipaResourceWithState<?>> requiresWrite = []
	private Set<CipaResourceWithState<?>> provides = []

	AbstractCipaActivity(Cipa cipa) {
		super(cipa)
	}

	@Override
	@NonCPS
	String getName() {
		return getClass().getSimpleName()
	}

	@NonCPS
	protected final void addRunRequiresRead(CipaResourceWithState<?> resourceWithState) {
		requiresRead.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
	}

	@NonCPS
	protected final void addRunRequiresWrite(CipaResourceWithState<?> resourceWithState) {
		requiresWrite.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
	}

	@NonCPS
	protected final void addRunProvides(CipaResourceWithState<?> resourceWithState) {
		provides.add(resourceWithState)
		cipa.addBean(resourceWithState.resource)
		cipa.addBean(resourceWithState)
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
