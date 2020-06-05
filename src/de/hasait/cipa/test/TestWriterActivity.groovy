/*
 * Copyright (C) 2017 by Sebastian Hasait (sebastian at hasait dot de)
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

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaNode
import de.hasait.cipa.activity.AbstractCipaActivity
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class TestWriterActivity extends AbstractCipaActivity {

	private final String name
	private final CipaResourceWithState<CipaFileResource> filesOut

	private final Boolean failingTest
	private String parent

	TestWriterActivity(Cipa cipa, String name, CipaResourceWithState<CipaFileResource> filesIn, String newState, Boolean failingTest = null) {
		super(cipa)

		this.name = name

		this.filesOut = modifiesResource(filesIn, newState)

		this.failingTest = failingTest
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> getProvidedFilesOut() {
		return filesOut
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	@NonCPS
	TestWriterActivity withParent(String parent) {
		this.parent = parent
		return this
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		script.echo("TestWriter ${filesOut}")

		script.dir(filesOut.resource.path) {
			script.mvn(['clean', 'package'], [], [], [], true)
			runContext.archiveMvnLogFile(getName() + '.log')
			runContext.addJUnitTestResults(null, '.*STest')
		}

		script.echo(script.pwd())
		script.dir("somedir") {
			script.echo(script.pwd())
		}

		if (failingTest != null ? failingTest : Math.random() < 0.2) {
			runContext.addFailedTest('Evil Test', 3)
		} else {
			runContext.addPassedTest('Good one')
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "TestWriter ${filesOut}"
	}

}
