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
import de.hasait.cipa.CipaInit
import de.hasait.cipa.PScript

/**
 * Wrap each activity in a stage.
 */
class StageAroundActivity implements CipaInit, CipaAroundActivity, Serializable {

	public static final int AROUND_ACTIVITY_ORDER = 100

	private final boolean withStageDefault

	StageAroundActivity(boolean withStageDefault = true) {
		this.withStageDefault = withStageDefault
	}

	private PScript script

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return AROUND_ACTIVITY_ORDER
	}

	@Override
	void handleFailedDependencies(CipaActivityInfo activityInfo) {
		// nop
	}

	@Override
	void beforeActivityStarted(CipaActivityInfo activityInfo) {
		// nop
	}

	@Override
	void runAroundActivity(CipaActivityInfo activityInfo, Closure<?> next) {
		boolean withStage = withStageDefault

		CipaActivity activity = activityInfo.activity

		if (activity instanceof CipaActivityWithStage) {
			withStage = ((CipaActivityWithStage) activity).withStage
		}

		if (withStage) {
			script.stage(activity.name, next)
		} else {
			next.call()
		}
	}

	@Override
	void afterActivityFinished(CipaActivityInfo activityInfo) {
		// nop
	}

}
