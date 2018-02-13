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
import de.hasait.cipa.CipaInit
import de.hasait.cipa.PScript
import de.hasait.cipa.internal.CipaActivityWrapper

/**
 * Wrap each activity in a timeout if CipaActivityWithTimeout is implemented.
 */
class TimeoutAroundActivity implements CipaInit, CipaAroundActivity, Serializable {

	private final Integer defaultTimeoutInMinutes

	TimeoutAroundActivity(Integer defaultTimeoutInMinutes = null) {
		this.defaultTimeoutInMinutes = defaultTimeoutInMinutes
	}

	private PScript script

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return 10000
	}

	@Override
	void handleFailedDependencies(CipaActivityWrapper wrapper) {
		// nop
	}

	@Override
	void beforeActivityStarted(CipaActivityWrapper wrapper) {
		// nop
	}

	@Override
	void runAroundActivity(CipaActivityWrapper wrapper, Closure<?> next) {
		Integer timeoutInMinutes = defaultTimeoutInMinutes

		CipaActivity activity = wrapper.activity

		if (activity instanceof CipaActivityWithTimeout) {
			timeoutInMinutes = ((CipaActivityWithTimeout) activity).timeoutInMinutes
		}

		if (timeoutInMinutes) {
			script.timeout(timeoutInMinutes, next)
		} else {
			next.call()
		}
	}

	@Override
	void afterActivityFinished(CipaActivityWrapper wrapper) {
		// nop
	}

}
