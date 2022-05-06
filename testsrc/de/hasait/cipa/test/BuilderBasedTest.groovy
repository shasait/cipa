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

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue

import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.testsupport.CipaTestBase
import org.junit.Before
import org.junit.Test

/**
 *
 */
class BuilderBasedTest extends CipaTestBase {

	@Before
	void init() {
		initCipa()
	}

	@Test
	void test_run_succeeds() {
		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml').setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		def node = cipa.newNode('label')

		def a1 = cipa.newActivity(node)
		def a1out = a1.providesDir('mydir')
		a1.create('A1') {
			script.dir(a1out.resource.path) {
				script.sh 'touch a'
			}
		}

		def a2 = cipa.newActivity(node)
		def a2out = a2.modifies(a1out)
		a2.create('A2') {
			script.dir(a2out.resource.path) {
				script.sh 'echo "Hallo" > a'
			}
		}

		cipa.run()
	}

	@Test
	void test_dependsOn_fullDep() {
		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml').setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		def node = cipa.newNode('label')

		def a1 = cipa.newActivity(node)
		def a1out = a1.providesDir('mydir')
		a1.create('A1') {
			script.dir(a1out.resource.path) {
				script.sh 'touch a'
			}
		}

		def a2 = cipa.newActivity(node)
		def a2out = a2.modifies(a1out)
		a2.create('A2') {
			script.dir(a2out.resource.path) {
				script.sh 'echo "Hallo" > a'
			}
		}

		cipa.run()

		Map<String, CipaActivityWrapper> wrapperByName = new HashMap<>()
		cipa.activityInfos.each {
			CipaActivityWrapper wrapper = it as CipaActivityWrapper
			wrapperByName.put(wrapper.activity.name, wrapper)
		}

		assertNull('A1 not depends on A2', wrapperByName.get('A1').dependsOn.get(wrapperByName.get('A2')))
		assertTrue('A2 depends on A1', wrapperByName.get('A2').dependsOn.get(wrapperByName.get('A1')))
	}

	@Test
	void test_dependsOn_orderWriters() {
		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml').setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		def node = cipa.newNode('label')

		def a1 = cipa.newActivity(node)
		def a1out = a1.providesDir('mydir')
		a1.create('A1') {
			script.dir(a1out.resource.path) {
				script.sh 'touch a'
			}
		}

		def a2 = cipa.newActivity(node)
		def a2out = a2.modifies(a1out)
		a2.create('A2') {
			script.dir(a2out.resource.path) {
				script.sh 'echo "Hallo" > a'
			}
		}

		def a3 = cipa.newActivity(node)
		def a3out = a3.modifies(a1out)
		a3.create('A3') {
			script.dir(a3out.resource.path) {
				script.sh 'echo "World" > a'
			}
		}

		cipa.run()

		Map<String, CipaActivityWrapper> wrapperByName = new HashMap<>()
		cipa.activityInfos.each {
			CipaActivityWrapper wrapper = it as CipaActivityWrapper
			wrapperByName.put(wrapper.activity.name, wrapper)
		}

		assertNull('A1 not depends on A2', wrapperByName.get('A1').dependsOn.get(wrapperByName.get('A2')))
		assertTrue('A2 depends on A1', wrapperByName.get('A2').dependsOn.get(wrapperByName.get('A1')))
		assertFalse('A3 depends on A2 only for write ordering', wrapperByName.get('A3').dependsOn.get(wrapperByName.get('A2')))
		assertNull('A2 not depends on A3', wrapperByName.get('A2').dependsOn.get(wrapperByName.get('A3')))
	}

	@Test
	void test_dependsOn_orderWriters_but_fullDeps_win() {
		cipa.configureJDK('JDK8')
		cipa.configureMaven('M3', 'ciserver-settings.xml', 'ciserver-toolchains.xml').setOptions('-Xms1g -Xmx4g -XX:ReservedCodeCacheSize=256m -Dproject.build.sourceEncoding=UTF-8 -Dfile.encoding=UTF-8 -Dmaven.compile.fork=true')

		def node = cipa.newNode('label')

		def a1 = cipa.newActivity(node)
		def a1out = a1.providesDir('mydir')
		a1.create('A1') {
			script.dir(a1out.resource.path) {
				script.sh 'touch a'
			}
		}

		def a2 = cipa.newActivity(node)
		def a2out = a2.modifies(a1out)
		a2.create('A2') {
			script.dir(a2out.resource.path) {
				script.sh 'echo "Hallo" > a'
			}
		}

		def a3 = cipa.newActivity(node)
		def a3out = a3.modifies(a1out)
		a3.reads(a2out)
		a3.create('A3') {
			script.dir(a3out.resource.path) {
				script.sh 'echo "World" > a'
			}
		}

		cipa.run()

		Map<String, CipaActivityWrapper> wrapperByName = new HashMap<>()
		cipa.activityInfos.each {
			CipaActivityWrapper wrapper = it as CipaActivityWrapper
			wrapperByName.put(wrapper.activity.name, wrapper)
		}

		assertNull('A1 not depends on A2', wrapperByName.get('A1').dependsOn.get(wrapperByName.get('A2')))
		assertTrue('A2 depends on A1', wrapperByName.get('A2').dependsOn.get(wrapperByName.get('A1')))
		assertTrue('A3 depends on A2 - write ordering and full dependency ', wrapperByName.get('A3').dependsOn.get(wrapperByName.get('A2')))
		assertNull('A2 not depends on A3', wrapperByName.get('A2').dependsOn.get(wrapperByName.get('A3')))
	}

}
