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
import de.hasait.cipa.JobParameterContainer
import de.hasait.cipa.JobParameterContribution
import de.hasait.cipa.JobParameterValues
import de.hasait.cipa.Script
import de.hasait.cipa.activity.CheckoutActivity
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState

/**
 *
 */
class TestPipeline implements CipaInit, JobParameterContribution, CipaAroundActivity, Serializable {

	private final Cipa cipa
	private Script script
	private def rawScript

	TestPipeline(rawScript) {
		cipa = new Cipa(rawScript)
		cipa.addBean(this)
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(Script.class)
		rawScript = script.rawScript

		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml').setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		CipaNode node1 = cipa.newNode('node1')
		CipaNode node2 = cipa.newNode('node2')

		CipaResourceWithState<CipaFileResource> mainCodeCheckedOut = cipa.newFileResourceWithState(node1, 'mainCode', 'CheckedOut')
		cipa.addBean(new CheckoutActivity('Checkout', 'MAIN', mainCodeCheckedOut).excludeUser('autouser', 'robot'))
	}

	void run() {
		cipa.run()
	}

	@Override
	void contributeParameters(JobParameterContainer container) {

	}

	@Override
	void processParameters(JobParameterValues values) {

	}

	@Override
	void runAroundActivity(CipaActivity activity, Closure<?> next) {
		rawScript.setCustomBuildProperty(key: "${activity.name}-StartTime", value: new Date())
		try {
			script.echo("runAroundActivity ${activity.name}...")
			next.call()
		} catch (err) {
			script.echo("runAroundActivity caught: ${err}")
			script.echo("runAroundActivity caught: ${err?.class}")
			rawScript.setCustomBuildProperty(key: "${activity.name}-Failed", value: err.toString())
			throw err
		} finally {
			rawScript.setCustomBuildProperty(key: "${activity.name}-EndTime", value: new Date())
		}
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return 0
	}

}
