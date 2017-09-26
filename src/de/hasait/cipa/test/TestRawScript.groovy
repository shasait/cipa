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

import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 *
 */
class TestRawScript {

	private static void log(String message) {
		System.out.println('[' + Thread.currentThread().name + ']' + message)
	}

	def currentBuild = ['number': 123]

	def params = [:]

	Env env = new Env()

	Map<String, String> readFileContents = [:]
	Map<String, String> shResults = [:]

	void echo(String message) {
		log('[echo] ' + message)
	}

	String sh(Map args) {
		log('[sh] ' + args)

		String script = args['script']
		if (script) {
			for (e in shResults) {
				if (Pattern.compile(e.key).matcher(script).matches()) {
					return e.value
				}
			}
		}

		return ''
	}

	void parallel(Map branches) {
		log('[parallel] >>>')
		List<Thread> threadGroup = new ArrayList<>()
		for (branch in branches) {
			if (Runnable.class.isInstance(branch.value)) {
				threadGroup.add(new Thread((Runnable) branch.value, (String) branch.key))
			}
		}
		threadGroup.each { it.start() }
		threadGroup.each { it.join() }
		log('[parallel] <<<')
	}

	void node(String label = '<none>', Closure<?> body) {
		log('[node] >>> ' + label)
		body()
		log('[node] <<< ' + label)
	}

	String readFile(Map args) {
		log('[readFile] ' + args)
		String file = args['file']
		if (file) {
			for (e in readFileContents) {
				if (Pattern.compile(e.key).matcher(file).matches()) {
					return e.value
				}
			}
		}
		return null
	}

	void writeFile(Map args) {
		log('[writeFile] ' + args)
	}

	void withEnv(List assignments, Closure<?> body) {
		log('[withEnv] >>> ' + assignments)
		body()
		log('[withEnv] <<< ' + assignments)
	}

	void dir(String dir, Closure<?> body) {
		log('[dir] >>> ' + dir)
		body()
		log('[dir] <<< ' + dir)
	}

	void deleteDir() {
		log('[deleteDir]')
	}

	void waitUntil(Closure<Boolean> test) {
		while (!test.call()) {
			log('[waitUntil]')
			Thread.sleep(1000)
		}
	}

	void checkout(Map args) {
		log('[checkout] ' + args)
	}

	void stash(Map args) {
		log('[stash] ' + args)
	}

	void unstash(Map args) {
		log('[unstash] ' + args)
	}

	void unstash(String arg) {
		log('[unstash] ' + arg)
	}

	void stage(String stage, Closure<?> body) {
		log('[stage] >>> ' + stage)
		body()
		log('[stage] <<< ' + stage)
	}

	void junit(String arg) {
		log('[junit] ' + arg)
	}

	void archiveArtifacts(Map args) {
		log('[archiveArtifacts] ' + args)
	}

	Object tool(Map args) {
		return ['tool': args]
	}

	Object string(Map args) {
		return ['string': args]
	}

	Object logRotator(Map args) {
		return ['logRotator': args]
	}

	Object buildDiscarder(Object logRotator) {
		return ['buildDiscarder': logRotator]
	}

	Object parameters(List parameters) {
		return ['parameters': parameters]
	}

	Object pipelineTriggers(Object arg0) {
		return ['pipelineTriggers': arg0]
	}

	Object pollSCM(String cron) {
		return ['pollSCM': cron]
	}

	void properties(List properties) {
		log('[properties] ' + properties)
	}

	Object configFile(Map args) {
		return ['configFile': args]
	}

	void configFileProvider(List configFiles, Closure<?> body) {
		log('[configFileProvider] >>> ' + configFiles)
		body()
		log('[configFileProvider] <<< ' + configFiles)
	}

	void setCustomBuildProperty(Map args) {
		log('[setCustomBuildProperty] ' + args)
	}

	void sleep(int seconds) {
		log('[sleep] ' + seconds)
		Thread.sleep(TimeUnit.SECONDS.toMillis(seconds))
	}

	class Env {

		def environment = [:]

		def getProperty(String name) {
			return environment[name]
		}

		void setProperty(String name, Object value) {
			environment[name] = value
		}

	}

}
