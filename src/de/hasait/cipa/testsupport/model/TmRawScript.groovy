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

package de.hasait.cipa.testsupport.model

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import groovy.json.JsonSlurper
import hudson.model.Run
import hudson.model.TaskListener
import hudson.util.StreamTaskListener
import org.jenkinsci.plugins.custombuildproperties.CustomBuildPropertiesAction

/**
 * Dummy implementation of a workflow script and some important steps.
 */
class TmRawScript {

	private static TaskListener TASK_LISTENER = new StreamTaskListener(System.out, StandardCharsets.UTF_8)

	private static String LOG_TOPIC = TmRawScript.class.simpleName

	private static void log(String message) {
		TASK_LISTENER.getLogger().println('[    *][' + LOG_TOPIC + '][' + Thread.currentThread().name + ']' + message)
	}

	def currentBuild = ['number': 123]

	/**
	 * Build params
	 */
	def params = [:]

	/**
	 * Results for readFile: key is a regex.
	 */
	Map<String, String> readFileContents = [:]

	/**
	 * Results for libraryResource: key is a regex.
	 */
	Map<String, String> libraryResourceContents = [:]

	/**
	 * Results for sh: key is a regex.
	 */
	Map<String, String> shResults = [:]

	/**
	 * sh to really execute: list of regex.
	 */
	List<String> shExecute = []

	/**
	 * Results for httpRequest: key is a regex.
	 */
	Map<String, Object> httpRequestResults = [:]

	/**
	 * Use negative value to disable sleeping.
	 */
	long maxSleepTimeMillis = 5000

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
		for (e in readFileContents) {
			if (Pattern.compile(e.key).matcher(file).matches()) {
				return true
			}
		}
		return false
	}

	Object httpRequest(Map args) {
		log('[httpRequest] ' + args)
		return httpRequestResults.get(args.url)
	}

	String libraryResource(Map args) {
		log('[libraryResource] ' + args)
		String resource = args.resource
		if (resource) {
			for (e in libraryResourceContents) {
				if (Pattern.compile(e.key).matcher(resource).matches()) {
					return e.value
				}
			}
		}
		return null
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

	void setCustomBuildProperty(Map args) {
		log('[setCustomBuildProperty] ' + args)
		String key = args.key
		Object value = args.value
		Boolean onlySetIfAbsent = args.onlySetIfAbsent
		def rawBuild = currentBuild.get('rawBuild')
		if (rawBuild instanceof Run) {
			if (rawBuild.getAction(CustomBuildPropertiesAction.class) == null) {
				rawBuild.addAction(new CustomBuildPropertiesAction())
			}
			CustomBuildPropertiesAction action = rawBuild.getAction(CustomBuildPropertiesAction.class)
			if (onlySetIfAbsent != null && onlySetIfAbsent) {
				action.setPropertyIfAbsent(key, value)
			} else {
				action.setProperty(key, value)
			}
		}
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

	String tool(Map args) {
		log('[tool] ' + args)

		return '/jenkins/tools/' + args['type'] + '-' + args['name']
	}

	void sleep(int sleepTimeSeconds) {
		long sleepTimeMillis = TimeUnit.SECONDS.toMillis(sleepTimeSeconds)
		log('[sleep] ' + sleepTimeMillis + 'ms (limited to ' + maxSleepTimeMillis + 'ms)')
		long limitedSleepTimeMillis = maxSleepTimeMillis < 0 ? sleepTimeMillis : Math.min(sleepTimeMillis, maxSleepTimeMillis)
		Thread.sleep(limitedSleepTimeMillis)
	}

	void waitUntil(Closure<Boolean> test) {
		while (!test.call()) {
			log('[waitUntil]')
			Thread.sleep(1000)
		}
	}

	def env = new TestEnv()

	class TestEnv {

		def environment = [:]

		def getProperty(String name) {
			return environment[name]
		}

		void setProperty(String name, Object value) {
			environment[name] = value
		}

	}

	def manager = new TestManager()

	class TestManager {

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

	}

	def context = [(TaskListener.class): TASK_LISTENER]

	def getContext(Class type) {
		return context.get(type)
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

}
