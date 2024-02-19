/*
 * Copyright (C) 2024 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.testsupport.model


import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun

/**
 * <p>TestModel for {@link WorkflowJob}.</p>
 * <p>See {@link TmBase} for details.</p>
 */
class TmJob extends TmItem<WorkflowJob> {

	final List<TmRun> tmRuns = []

	int nextBuildNumber = 1

	TmJob(TmFactory tmFactory, TmItemGroup tmParent, String name) {
		super(WorkflowJob.class, tmFactory, tmParent, name)
	}

	TmRun getTmBuildByNumber(int number) {
		return tmRuns.find { it.number == number }
	}

	TmRun createTmRun() {
		return tmFactory.createTmRun(this, nextBuildNumber++)
	}

	WorkflowRun getBuildByNumber(int number) {
		return getTmBuildByNumber(number)?.mock
	}

	WorkflowRun getFirstBuild() {
		return tmRuns.empty ? null : tmRuns[0].mock
	}

	WorkflowRun getLastBuild() {
		return tmRuns.empty ? null : tmRuns.last().mock
	}

	WorkflowRun getLastSuccessfulBuild() {
		int i = tmRuns.findLastIndexOf { !it.building }
		return i < 0 ? null : tmRuns[i].mock
	}

}
