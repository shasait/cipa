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

package de.hasait.cipa.test

import static org.hamcrest.CoreMatchers.allOf
import static org.hamcrest.CoreMatchers.containsString

import hudson.model.Job
import hudson.model.Run
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExpectedException
import org.mockito.Mockito

/**
 *
 */
class RawScriptTestBase {

	TestRawScript rawScript

	Run currentBuildMock
	Job currentJobMock

	@Rule
	public ExpectedException thrown = ExpectedException.none()

	@Before
	void beforeTest() {
		rawScript = new TestRawScript()

		currentBuildMock = Mockito.mock(Run.class)
		currentJobMock = Mockito.mock(Job.class)
		Mockito.when(currentBuildMock.getParent()).thenReturn(currentJobMock)

		rawScript.currentBuild.rawBuild = currentBuildMock
	}

	void expectFailingActivites(Map<String, String> activityWithMessage) {
		thrown.expect(RuntimeException.class)
		thrown.expectMessage(allOf(activityWithMessage.collect { containsString("${it.key} = ${it.value}") }))
	}

}
