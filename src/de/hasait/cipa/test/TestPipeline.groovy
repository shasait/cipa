/*
 * Copyright (C) 2021 by Sebastian Hasait (sebastian at hasait dot de)
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
import de.hasait.cipa.activity.AbstractCipaAroundActivity
import de.hasait.cipa.activity.CheckoutActivity
import de.hasait.cipa.activity.CipaActivityInfo
import de.hasait.cipa.activity.StashFilesActivity
import de.hasait.cipa.activity.UnstashFilesActivity
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource
import org.jenkinsci.plugins.custombuildproperties.CustomBuildPropertiesAction

/**
 *
 */
class TestPipeline extends AbstractCipaAroundActivity implements CipaInit, Serializable {

	TestPipeline(rawScript, Boolean ra1f = null, Boolean ra2f = null, Boolean ra3f = null, Boolean wa1f = null, Boolean wa2f = null, Boolean wa3f = null, Boolean rb1f = null) {
		super(rawScript)

		cipa.debug = true

		cipa.addStandardBeans(10)

		CipaNode node1 = cipa.newNode('node1')
		CipaNode node2 = cipa.newNode('node2')

		CipaResourceWithState<CipaFileResource> mainCheckedOutFiles = new CheckoutActivity(cipa, 'Checkout', 'Main', node1).excludeUser('autouser', 'robot').providedCheckedOutFiles
		CipaResourceWithState<CipaStashResource> mainStash = new StashFilesActivity(cipa, 'StashMain', mainCheckedOutFiles).providedStash
		CipaResourceWithState<CipaFileResource> files = new UnstashFilesActivity(cipa, "UnstashMain", mainStash, node2).providedFiles
		new TestReaderActivity(cipa, "TestRa1", files, ra1f)
		new TestReaderActivity(cipa, "TestRa2", files, ra2f)
		new TestReaderActivity(cipa, "TestRa3", files, ra3f)
		new TestWriterActivity(cipa, "TestWa1", files, "StateW1", wa1f)
		new TestWriterActivity(cipa, "TestWa2", files, "StateW2", wa2f)
		CipaResourceWithState<CipaFileResource> filesW3 = new TestWriterActivity(cipa, "TestWa3", files, "StateW3", wa3f).providedFilesOut
		new TestReaderActivity(cipa, "TestRb1", filesW3, rb1f)
	}

	@Override
	void initCipa(Cipa cipa) {
		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml')
				.setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		script.setCustomBuildProperty("${CustomBuildPropertiesAction.CBP_TABLE_PREFIX}Activities", "Activity-(.*?)-(.*)")
	}

	void run() {
		cipa.run()
	}

	@Override
	void handleFailedDependencies(CipaActivityInfo activityInfo) {
		script.setCustomBuildProperty("Activity-${activityInfo.activity.name}-DepsFailed", activityInfo.failedDependencies.size())
	}

	@Override
	void runAroundActivity(CipaActivityInfo activityInfo, Closure<?> next) {
		String name = activityInfo.activity.name
		script.setCustomBuildProperty("Activity-${name}-BeginTime", activityInfo.startedDate)
		next.call()
	}

	@Override
	void afterActivityFinished(CipaActivityInfo activityInfo) {
		String name = activityInfo.activity.name
		if (activityInfo.failed) {
			script.setCustomBuildProperty("Activity-${name}-Failed", activityInfo.buildFailedMessage())
		}
		// Activity-${name}-Finished set by cipa internally
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return 0
	}

}
