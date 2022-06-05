/*
 * Copyright (C) 2022 by Sebastian Hasait (sebastian at hasait dot de)
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

import hudson.model.Cause
import hudson.model.Result
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun

/**
 * <p>TestModel for {@link WorkflowRun}.</p>
 * <p>See {@link TmBase} for details.</p>
 */
class TmRun extends TmActionable<WorkflowRun> {

	final TmJob tmJob
	final int number

	boolean building
	Result result

	List<String> log = []

	List<Cause> causes = []

	TmRun(TmFactory tmFactory, TmJob tmJob, int number) {
		super(WorkflowRun.class, tmFactory)

		this.tmJob = tmJob
		this.number = number
		this.tmJob.tmRuns.add(this)
		result = Result.SUCCESS
	}

	WorkflowJob getParent() {
		return tmJob.mock
	}

	WorkflowRun getPreviousBuild() {
		return tmJob.getBuildByNumber(number - 1)
	}

	WorkflowRun getNextBuild() {
		return tmJob.getBuildByNumber(number + 1)
	}

	String getAbsoluteUrl() {
		return tmJob.absoluteUrl + number + '/'
	}

	public <T extends Cause> T getCause(Class<T> type) {
		for (Cause cause in causes) {
			if (type.isInstance(cause)) {
				return type.cast(cause)
			}
		}
		return null
	}

	String getExternalizableId() {
		return tmJob.fullName + '#' + number
	}

	List<String> getLog(int maxLines) {
		if (log.empty || maxLines == 0) {
			return []
		}
		int size = log.size()
		return log.subList(Math.max(0, size - maxLines), size).collect()
	}

}
