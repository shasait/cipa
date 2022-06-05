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

import de.hasait.cipa.testsupport.model.TmFactory
import de.hasait.cipa.testsupport.model.TmJenkins
import jenkins.model.Jenkins
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles
import org.mockito.Mockito

/**
 * Base class for Tests. Call {@link JenkinsTestBase#initJenkins()} to initialize mocking.
 */
class JenkinsTestBase {

	/**
	 * TestModel.
	 */
	TmJenkins tmJenkins
	/**
	 * Mock delegating to tmJenkins.
	 */
	Jenkins jenkins

	/**
	 * Mock.
	 */
	GlobalConfigFiles globalConfigFiles

	void initJenkins(TmFactory tmFactory = new TmFactory()) {
		tmJenkins = tmFactory.createTmJenkins()
		jenkins = tmJenkins.mock

		globalConfigFiles = Mockito.mock(GlobalConfigFiles.class)
		tmJenkins.createTmExtensionList(GlobalConfigFiles.class).add(globalConfigFiles)
	}

}
