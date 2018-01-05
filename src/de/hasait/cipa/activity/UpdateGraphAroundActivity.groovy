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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaInit
import de.hasait.cipa.PScript
import de.hasait.cipa.internal.CipaActivityWrapper

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class UpdateGraphAroundActivity implements CipaInit, CipaAroundActivity, CipaAfterActivities, Serializable {

	private final AtomicBoolean initCalled = new AtomicBoolean()
	private final AtomicReference<String> svgContentHolder = new AtomicReference<>()

	private Cipa cipa
	private PScript script

	@Override
	void initCipa(Cipa cipa) {
		this.cipa = cipa
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	String toString() {
		return "Create activity graph"
	}

	@Override
	void handleFailedDependencies(CipaActivityWrapper wrapper) {
		// nop
	}

	@Override
	void beforeActivityStarted(CipaActivityWrapper wrapper) {
		if (!svgContentHolder.get()) {
			if (initCalled.compareAndSet(false, true)) {
				init()
			}
		}
	}

	@Override
	void runAroundActivity(CipaActivityWrapper wrapper, Closure<?> next) {
		updateGraph()
		next.call()
	}

	@Override
	void afterActivityFinished(CipaActivityWrapper wrapper) {
		updateGraph()
	}

	@Override
	@NonCPS
	int getRunAroundActivityOrder() {
		return 1000
	}

	@Override
	void afterCipaActivities() {
		updateGraph()
	}

	private void init() {
		script.echo("Creating activity graph...")

		script.dir(this.class.simpleName) {
			String basename = 'activities-graph'
			String dotFilename = "${basename}.graphviz"
			String svgFilename = "${basename}.svg"
			script.writeFile(dotFilename, cipa.runContext.dotContent)
			script.sh("( test -x /usr/bin/dot && /usr/bin/dot -Tpng ${dotFilename} > ${basename}.png && /usr/bin/dot -Tsvg ${dotFilename} > ${svgFilename} ) || true")
			script.archiveArtifacts("${basename}.*")
			try {
				String svgContent = script.readFile(svgFilename)
				svgContentHolder.set(svgContent)
			} catch (err) {
				// ignore
			}
		}

		updateGraph()
	}

	@NonCPS
	private String produceSVG() {
		String svgContent = svgContentHolder.get()
		if (svgContent) {
			for (wrapperWithNodeName in cipa.runContext.dotNodeNameByWrappers) {
				CipaActivityWrapper wrapper = wrapperWithNodeName.key
				String nodeName = wrapperWithNodeName.value
				boolean running = wrapper.running
				boolean failed = wrapper.failed
				boolean depsFailed = wrapper.failedDependencies
				boolean done = wrapper.done
				boolean stable = wrapper.testResults.stable
				String fill = depsFailed ? "gray" : (failed ? "red" : (done ? (stable ? "lightgreen" : "yellow") : "none"))
				String stroke = running ? "blue" : "black"
				String title = wrapper.activity.name.replace('<', '$lt;').replace('>', '$gt;')
				svgContent = Pattern.compile("(<title>)${nodeName}(</title>\\s+<[a-z]+ fill=\")[^\"]+(\" stroke=\")[^\"]+(\")").matcher(svgContent).replaceFirst("\$1${title}\$2${fill}\$3${stroke}\$4")
			}
		}
		return svgContent
	}

	private void updateGraph() {
		String svgContent = produceSVG()
		if (svgContent) {
			try {
				script.rawScript.setCustomBuildProperty(key: "Activity-Graph", value: svgContent)
			} catch (err) {
				// ignore
			}
		}
	}

}
