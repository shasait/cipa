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

package de.hasait.cipa.activity

import de.hasait.cipa.CipaNode
import de.hasait.cipa.resource.CipaResourceWithState

/**
 * An activity is the core part of a Cipa pipeline. It requires resources for execution and provides resources afterwards.
 */
interface CipaActivity {

	/**
	 * @return The unique name (should be short).
	 */
	String getName()

	/**
	 * @return The required resources for reading; can be empty.
	 */
	Set<CipaResourceWithState<?>> getRunRequiresRead()

	/**
	 * @return The required resources for writing; can be empty.
	 */
	Set<CipaResourceWithState<?>> getRunRequiresWrite()

	/**
	 * @return These resources are available after this activity has run; can be empty.
	 */
	Set<CipaResourceWithState<?>> getRunProvides()

	CipaNode getNode()

	/**
	 * Executed before any activity's #runActivity is called.
	 */
	void prepareNode()

	/**
	 * Perform the logic of the activity.
	 */
	void runActivity()

}
