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

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa

/**
 * Wrap each activity in a timeout if defaultTimeout is specified.
 * {@link CipaActivityWithTimeout} can be implemented by an activity to adjust the timeout.
 */
class TimeoutAroundActivity extends AbstractCipaAroundActivity {

	public static final int AROUND_ACTIVITY_ORDER = 10000

	private Integer defaultTimeoutInMinutes

	TimeoutAroundActivity(Cipa cipa, Integer defaultTimeoutInMinutes = null) {
		super(cipa)

		this.defaultTimeoutInMinutes = defaultTimeoutInMinutes
	}

	@NonCPS
	TimeoutAroundActivity withDefaultTimeoutInMinutes(Integer defaultTimeoutInMinutes) {
		this.defaultTimeoutInMinutes = defaultTimeoutInMinutes
		return this
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return AROUND_ACTIVITY_ORDER
	}

	@Override
	void runAroundActivity(CipaActivityInfo activityInfo, Closure<?> next) {
		Integer timeoutInMinutes = defaultTimeoutInMinutes

		CipaActivity activity = activityInfo.activity

		if (activity instanceof CipaActivityWithTimeout) {
			timeoutInMinutes = ((CipaActivityWithTimeout) activity).timeoutInMinutes
		}

		if (timeoutInMinutes) {
			script.timeout(timeoutInMinutes, next)
		} else {
			next.call()
		}
	}

}
