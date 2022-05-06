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

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.activity.AbstractCipaActivity
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

class TestReaderActivity extends AbstractCipaActivity {

	private final String name
	private final CipaResourceWithState<CipaFileResource> filesIn

	private final Boolean failing

	TestReaderActivity(Cipa cipa, String name, CipaResourceWithState<CipaFileResource> filesIn, Boolean failing = null) {
		super(cipa)

		this.name = name

		this.filesIn = filesIn
		addRunRequiresRead(filesIn)

		this.failing = failing
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		script.echo("TestReader ${filesIn}")

		script.echo(script.pwd())
		script.dir("somedir") {
			script.dir("somemoredir") {
				script.echo(script.pwd())
			}
		}

		script.sleep(1)
		if (failing != null ? failing : Math.random() < 0.2) {
			throw new RuntimeException("Failure")
		}
		script.sleep(1)
	}

	@Override
	@NonCPS
	String toString() {
		return "TestReader ${filesIn}"
	}

}
