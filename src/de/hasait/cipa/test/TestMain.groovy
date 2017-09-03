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
/**
 *
 */
class TestMain {

	static void main(String[] args) {
		System.out.println("Test")

		TestRawScript rawScript = new TestRawScript()
		rawScript.env.MAIN_SCM_URL = 'scm://somewhere.git'
		rawScript.env.MAIN_SCM_CREDENTIALS_ID = 'somecreds'
		rawScript.env.NODE_LABEL_PREFIX = 'nlprefix-'

		TestPipeline testPipeline = new TestPipeline(rawScript)
		testPipeline.run()
	}

}
