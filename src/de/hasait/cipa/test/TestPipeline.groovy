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
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaNode
import de.hasait.cipa.PScript
import de.hasait.cipa.activity.CheckoutActivity
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.activity.StageAroundActivity
import de.hasait.cipa.activity.StashFilesActivity
import de.hasait.cipa.activity.TimeoutAroundActivity
import de.hasait.cipa.activity.UnstashFilesActivity
import de.hasait.cipa.activity.UpdateGraphAroundActivity
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource

/**
 *
 */
class TestPipeline implements CipaInit, CipaAroundActivity, Serializable {

	private final Cipa cipa
	private PScript script
	private def rawScript

	TestPipeline(rawScript, Boolean ra1f = null, Boolean ra2f = null, Boolean ra3f = null, Boolean wa1f = null, Boolean wa2f = null, Boolean wa3f = null, Boolean rb1f = null) {
		cipa = new Cipa(rawScript)
		cipa.debug = true
		cipa.addBean(this)

		CipaNode node1 = cipa.newNode('node1')
		CipaNode node2 = cipa.newNode('node2')

		cipa.addBean(new StageAroundActivity())
		new TimeoutAroundActivity(cipa, 10)
		cipa.addBean(new UpdateGraphAroundActivity())

		CipaResourceWithState<CipaFileResource> mainCheckedOutFiles = new CheckoutActivity(cipa, 'Checkout', 'Main', node1).excludeUser('autouser', 'robot').providedCheckedOutFiles
		CipaResourceWithState<CipaStashResource> mainStash = new StashFilesActivity(cipa, 'StashMain', mainCheckedOutFiles).providedStash
		CipaResourceWithState<CipaFileResource> files = new UnstashFilesActivity(cipa, "UnstashMain", mainStash, node2).providedFiles
		new TestReaderActivity(cipa, "TestRa1", files, "StateRa1", ra1f)
		TestReaderActivity ra2 = new TestReaderActivity(cipa, "TestRa2", files, "StateRa2", ra2f)
		new TestReaderActivity(cipa, "TestRa3", files, "StateRa3", ra3f)
		new TestWriterActivity(cipa, "TestWa1", files, "StateW1", wa1f)
		new TestWriterActivity(cipa, "TestWa2", files, "StateW2", wa2f)
		new TestWriterActivity(cipa, "TestWa3", files, "StateW3", wa3f)
		new TestReaderActivity(cipa, "TestRb1", ra2.providedFilesOut, "StateRb1", rb1f)
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript

		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml')
				.setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')
	}

	void run() {
		cipa.run()
	}

	@Override
	void handleFailedDependencies(CipaActivityWrapper wrapper) {
		rawScript.setCustomBuildProperty(key: "${wrapper.activity.name}-DepsFailed", value: wrapper.failedDependencies.size())
	}

	@Override
	void beforeActivityStarted(CipaActivityWrapper wrapper) {
	}

	@Override
	void runAroundActivity(CipaActivityWrapper wrapper, Closure<?> next) {
		String name = wrapper.activity.name
		rawScript.setCustomBuildProperty(key: "${name}-BeginTime", value: wrapper.startedDate)
		script.echo("runAroundActivity ${name}...")
		next.call()
	}

	@Override
	void afterActivityFinished(CipaActivityWrapper wrapper) {
		String name = wrapper.activity.name
		if (wrapper.failed) {
			rawScript.setCustomBuildProperty(key: "${name}-Failed", value: wrapper.buildFailedMessage())
		}
		rawScript.setCustomBuildProperty(key: "${name}-EndTime", value: wrapper.finishedDate)
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return 0
	}

}
