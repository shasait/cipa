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

package de.hasait.cipa.testsupport

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString

import de.hasait.cipa.testsupport.model.TmJob
import de.hasait.cipa.testsupport.model.TmRawScript
import de.hasait.cipa.testsupport.model.TmRun
import org.junit.Rule
import org.junit.rules.ExpectedException

/**
 *
 */
class RawScriptTestBase extends JenkinsTestBase {

	static final String DEFAULT_CURRENT_JOB_FQN = 'Level1/Level2/SomeJob'

	TmRawScript rawScript

	TmJob currentJob
	TmRun currentBuild

	@Rule
	public ExpectedException thrown = ExpectedException.none()

	void initRawScript(String currentJobFullQualifiedName = DEFAULT_CURRENT_JOB_FQN) {
		initJenkins()

		rawScript = new TmRawScript()
		currentJob = tmJenkins.getOrCreateTmJob(currentJobFullQualifiedName)
		currentBuild = new TmRun(currentJob)
		rawScript.currentBuild.rawBuild = currentBuild.mock
	}

	void expectFailingActivities(Map<String, String> activityWithMessage) {
		thrown.expect(RuntimeException.class)
		thrown.expectMessage(allOf(activityWithMessage.collect { containsString("${it.key} = ${it.value}") }))
	}

}
