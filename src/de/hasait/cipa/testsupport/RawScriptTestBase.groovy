/*
 * Copyright (C) 2023 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.testsupport

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString

import de.hasait.cipa.testsupport.model.TmFactory
import de.hasait.cipa.testsupport.model.TmJob
import de.hasait.cipa.testsupport.model.TmRawScript
import de.hasait.cipa.testsupport.model.TmRun
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.job.WorkflowRun
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper
import org.junit.Rule
import org.junit.rules.ExpectedException

/**
 * Base class for Tests. Call {@link RawScriptTestBase#initRawScript()} to initialize mocking.
 */
class RawScriptTestBase extends JenkinsTestBase {

	static final String DEFAULT_CURRENT_JOB_FQN = 'Level1/Level2/SomeJob'

	/**
	 * TestModel RawScript.
	 */
	TmRawScript rawScript

	/**
	 * TestModel for current running job.
	 */
	TmJob currentTmJob
	/**
	 * Mock delegating to currentTmJob.
	 */
	WorkflowJob currentJob
	/**
	 * TestModel for current running build.
	 */
	TmRun currentTmRun
	/**
	 * Mock delegating to currentTmRun.
	 */
	WorkflowRun currentRun

	@Rule
	public ExpectedException thrown = ExpectedException.none()

	void initRawScript(String currentJobFullQualifiedName = DEFAULT_CURRENT_JOB_FQN, TmFactory tmFactory = new TmFactory()) {
		initJenkins(tmFactory)

		rawScript = tmFactory.createTmRawScript()
		currentTmJob = tmJenkins.getOrCreateTmJob(currentJobFullQualifiedName)
		currentJob = currentTmJob.mock
		currentTmRun = currentTmJob.createTmRun()
		currentTmRun.building = true
		currentRun = currentTmRun.mock
		RunWrapper runWrapper = new RunWrapper(currentRun, true)
		rawScript.currentBuild = runWrapper
	}

	void expectFailingActivities(Map<String, String> activityWithMessage) {
		thrown.expect(RuntimeException.class)
		thrown.expectMessage(allOf(activityWithMessage.collect { containsString("${it.key} = ${it.value}") }))
	}

}
