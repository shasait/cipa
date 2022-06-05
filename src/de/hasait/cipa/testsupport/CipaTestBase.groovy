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

import de.hasait.cipa.Cipa
import de.hasait.cipa.PScript
import de.hasait.cipa.testsupport.model.TmFactory

/**
 * Base class for Tests. Call {@link CipaTestBase#initCipa()} to initialize mocking.
 */
class CipaTestBase extends RawScriptTestBase {

	/**
	 * Non-mock instance.
	 */
	Cipa cipa
	/**
	 * Non-mock instance.
	 */
	PScript script

	void initCipa(String currentJobFullQualifiedName = DEFAULT_CURRENT_JOB_FQN, TmFactory tmFactory = new TmFactory()) {
		initRawScript(currentJobFullQualifiedName, tmFactory)

		cipa = Cipa.getOrCreate(rawScript)
		script = cipa.findBean(PScript.class)

		cipa.debug = true
	}

}
