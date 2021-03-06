/*
 * Copyright (C) 2020 by Sebastian Hasait (sebastian at hasait dot de)
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

/**
 * Aspects for Activities.
 */
interface CipaAroundActivity {

	/**
	 * Any dependency failed.
	 */
	void handleFailedDependencies(CipaActivityInfo activityInfo)

	/**
	 * Before startTime is set.
	 */
	void beforeActivityStarted(CipaActivityInfo activityInfo)

	/**
	 * Around run of activity.
	 */
	void runAroundActivity(CipaActivityInfo activityInfo, Closure<?> next)

	/**
	 * After finishedTime was set.
	 */
	void afterActivityFinished(CipaActivityInfo activityInfo)

	/**
	 * @return Value for ordering: Higher means later in chain.
	 */
	int getRunAroundActivityOrder()

}
