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

package de.hasait.cipa.test

import static org.mockito.Mockito.when

import de.hasait.cipa.testsupport.CipaTestBase
import hudson.tasks.junit.CaseResult
import hudson.tasks.junit.TestResultAction
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

/**
 *
 */
class TestPipelineTest extends CipaTestBase {

	@Before
	void init() {
		initCipa()

		rawScript.maxSleepTimeMillis = 100
	}

	@Test
	void testTestPipelineAllGreen() {
		TestResultAction testResultAction = Mockito.mock(TestResultAction)
		CaseResult caseResult1 = Mockito.mock(CaseResult)
		when(caseResult1.getClassName()).thenReturn('foo.bar.SddTest')
		when(testResultAction.getPassedTests()).thenReturn([caseResult1])
		currentTmRun.addAction(testResultAction)
		currentTmJob.description = '''
vvv parameters.json vvv {
  "MAIN_SCM_URL": "scm://somewhere.git"
, "MAIN_SCM_CREDENTIALS_ID": "somecreds"
} ^^^ parameters.json ^^^
'''

		TestPipeline testPipeline = new TestPipeline(rawScript, false, false, false, false, false, false, false)
		testPipeline.run()
	}

	@Test
	void testTestPipelineRa1Fails() {
		expectFailingActivities(['TestRa1': 'runActivity or runAroundActivity failed - Failure'])

		TestResultAction testResultAction = Mockito.mock(TestResultAction)
		CaseResult caseResult1 = Mockito.mock(CaseResult)
		when(caseResult1.getClassName()).thenReturn('foo.bar.SddTest')
		when(testResultAction.getPassedTests()).thenReturn([caseResult1])
		currentTmRun.addAction(testResultAction)
		currentTmJob.description = '''
vvv parameters.json vvv {
  "MAIN_SCM_URL": "scm://somewhere.git"
, "MAIN_SCM_CREDENTIALS_ID": "somecreds"
} ^^^ parameters.json ^^^
'''

		TestPipeline testPipeline = new TestPipeline(rawScript, true, false, false, false, false, false, false)
		testPipeline.run()
	}

	@Test
	void testTestPipelineRa1AndRa3Fails() {
		expectFailingActivities(['TestRa1': 'runActivity or runAroundActivity failed - Failure', 'TestRa3': 'runActivity or runAroundActivity failed - Failure'])

		TestResultAction testResultAction = Mockito.mock(TestResultAction)
		CaseResult caseResult1 = Mockito.mock(CaseResult)
		when(caseResult1.getClassName()).thenReturn('foo.bar.SddTest')
		when(testResultAction.getPassedTests()).thenReturn([caseResult1])
		currentTmRun.addAction(testResultAction)
		currentTmJob.description = '''
vvv parameters.json vvv {
  "MAIN_SCM_URL": "scm://somewhere.git"
, "MAIN_SCM_CREDENTIALS_ID": "somecreds"
} ^^^ parameters.json ^^^
'''

		TestPipeline testPipeline = new TestPipeline(rawScript, true, false, true, false, false, false, false)
		testPipeline.run()
	}

}
