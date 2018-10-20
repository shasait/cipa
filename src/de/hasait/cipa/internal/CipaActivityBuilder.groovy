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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaNode
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState

/**
 *
 */
class CipaActivityBuilder implements Serializable {

	private final Cipa cipa
	private final CipaNode node

	private final Set<CipaResourceWithState<?>> activityRequiresRead = []
	private final Set<CipaResourceWithState<?>> activityRequiresWrite = []
	private final Set<CipaResourceWithState<?>> activityProvides = []

	private boolean used = false

	CipaActivityBuilder(Cipa cipa, CipaNode node) {
		this.cipa = cipa
		this.node = node
	}

	@NonCPS
	private String newState() {
		return UUID.randomUUID().toString()
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> providesDir(String relDir, boolean global = false) {
		CipaResourceWithState<CipaFileResource> cipaResourceWithState = cipa.newFileResourceWithState(global ? null : node, relDir, newState())
		activityProvides.add(cipaResourceWithState)
		return cipaResourceWithState
	}

	@NonCPS
	public <R extends CipaResource> CipaResourceWithState<R> provides(R resource) {
		CipaResourceWithState<R> cipaResourceWithState = cipa.newResourceWithState(resource, newState())
		activityProvides.add(cipaResourceWithState)
		return cipaResourceWithState
	}

	@NonCPS
	public <R extends CipaResource> CipaResourceWithState<R> modifies(CipaResourceWithState<R> modified) {
		activityRequiresWrite.add(modified)
		CipaResourceWithState<R> newResourceState = cipa.newResourceState(modified, newState())
		activityProvides.add(newResourceState)
		return newResourceState
	}

	@NonCPS
	public <R extends CipaResource> void reads(CipaResourceWithState<R> read) {
		activityRequiresRead.add(read)
	}

	@NonCPS
	CipaActivity create(String name, Closure<?> logic) {
		if (activityProvides && used) {
			throw new IllegalStateException("A builder having provided resources can be only used once: ${name}")
		}

		String activityName = name
		CipaNode activityNode = node
		Closure<?> activityLogic = logic

		CipaActivity activity = new CipaActivity() {

			@NonCPS
			String getName() {
				return activityName
			}

			@NonCPS
			Set<CipaResourceWithState<?>> getRunRequiresRead() {
				return activityRequiresRead
			}

			@NonCPS
			Set<CipaResourceWithState<?>> getRunRequiresWrite() {
				return activityRequiresWrite
			}

			@NonCPS
			Set<CipaResourceWithState<?>> getRunProvides() {
				return activityProvides
			}

			@NonCPS
			CipaNode getNode() {
				return activityNode
			}

			void prepareNode() {
				// empty
			}

			void runActivity(CipaActivityRunContext runContext) {
				activityLogic.call(runContext)
			}

		}

		used = true
		return cipa.addBean(activity)
	}

}
