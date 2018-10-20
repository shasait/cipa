/*
 * Copyright (C) 2018 by Sebastian Hasait (sebastian at hasait dot de)
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


import org.junit.Test

/**
 *
 */
class BuilderBasedTest extends CipaTestBase {

	@Test
	void testBuilder1() {
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

}
