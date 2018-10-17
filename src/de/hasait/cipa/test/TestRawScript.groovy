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

import groovy.json.JsonSlurper

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
	List<String> shExecute = []
	Map<String, Object> httpRequestResults = [:]

	ThreadLocal<List<String>> pwdListHolder = new ThreadLocal<>()

	void dir(String dir, Closure<?> body) {
		log('[dir] >>> ' + dir)
		List<String> pwdList = pwdListHolder.get() ?: []
		if (!pwdList) {
			pwdList = []
			pwdListHolder.set(pwdList)
		}
		pwdList.add(dir)
		try {
			body()
		} finally {
			pwdList.remove(pwdList.size() - 1)
		}
		log('[dir] <<< ' + dir)
	}

	boolean fileExists(String file) {
		return readFileContents.containsKey(file)
	}

	Object httpRequest(Map args) {
		log('[httpRequest] ' + args)
		return httpRequestResults.get(args.url)
	}

	void node(String label = '<none>', Closure<?> body) {
		log('[node] >>> ' + label)
		body()
		log('[node] <<< ' + label)
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

	String pwd() {
		log('[pwd]')
		List<String> pwdList = pwdListHolder.get() ?: []
		return pwdList ? pwdList.join('/') : ''
	}

	String readFile(Map args) {
		log('[readFile] ' + args)
		String file = args.file
		if (file) {
			for (e in readFileContents) {
				if (Pattern.compile(e.key).matcher(file).matches()) {
					return e.value
				}
			}
		}
		return null
	}

	Object readJSON(Map args) {
		log('[readJSON] ' + args)
		String file = args.file
		return new JsonSlurper().parseText(readFileContents.get(file))
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
			for (e in shExecute) {
				if (Pattern.compile(e).matcher(script).matches()) {
					Process process = script.execute()
					int exitValue = process.exitValue()
					if (!exitValue) {
						throw new RuntimeException("${script} failed: ${exitValue}")
					}
					return process.text
				}
			}
		}

		return ''
	}

	void sleep(int seconds) {
		log('[sleep] ' + seconds)
		Thread.sleep(TimeUnit.SECONDS.toMillis(seconds))
	}

	void waitUntil(Closure<Boolean> test) {
		while (!test.call()) {
			log('[waitUntil]')
			Thread.sleep(1000)
		}
	}

	//-----------------------------------------------

	def methodMissing(String name, args) {
		Closure body
		if (args && args.length > 0 && args[-1] instanceof Closure) {
			body = args[-1]
			args = args[0..-2]
		} else {
			body = null
		}
		log('[' + name + ']' + (body ? ' >>>' : '') + (args ? ' ' + args : ''))
		if (body) {
			body.call()
			log('[' + name + ']' + (body ? ' <<<' : '') + (args ? ' ' + args : ''))
		}

		def result = [:]
		result.put(name, args)
		return result
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
