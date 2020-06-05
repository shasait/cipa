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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaRunContext
import de.hasait.cipa.internal.CipaActivityWrapper

class UpdateGraphAroundActivity extends AbstractCipaAroundActivity implements CipaAfterActivities, Serializable {

	public static final int AROUND_ACTIVITY_ORDER = 1000

	private final Map<CipaActivityWrapper, String> dotNodeNameByWrappers = new HashMap<>()

	private final AtomicBoolean produceSVGCalled = new AtomicBoolean()

	private final AtomicReference<String> svgContentHolder = new AtomicReference<>()

	UpdateGraphAroundActivity(Cipa cipa) {
		super(cipa)
	}

	@Override
	@NonCPS
	String toString() {
		return "Create activity graph"
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
		return AROUND_ACTIVITY_ORDER
	}

	@Override
	void afterCipaActivities() {
		updateGraph()
	}

	private String produceSVG() {
		script.echo("Creating activity graph...")

		String svgContent

		script.dir(this.class.simpleName) {
			String basename = 'activities-graph'
			String dotFilename = "${basename}.graphviz.txt"
			String svgFilename = "${basename}.svg"
			String dotContent = produceDot()
			script.writeFile(dotFilename, dotContent)
			script.sh("( test -x /usr/bin/dot && /usr/bin/dot -Tpng ${dotFilename} > ${basename}.png && /usr/bin/dot -Tsvg ${dotFilename} > ${svgFilename} ) || true")
			script.archiveArtifacts("${basename}.*")
			svgContent = script.readFile(svgFilename)
		}

		return svgContent
	}

	@NonCPS
	private String produceDot() {
		CipaRunContext runContext = cipa.runContext

		StringBuilder dotContent = new StringBuilder()
		dotContent << '\n'
		dotContent << 'digraph pipeline {\n'
		dotContent << 'rankdir="LR";\n'
		dotContent << 'newrank=true;\n'
		dotContent << 'splines=ortho;\n'
		dotContent << 'node[shape=box];\n'
		int activityI = 0

		for (wrapper in runContext.wrappers) {
			dotNodeNameByWrappers.put(wrapper, "a${activityI++}")
		}
		int nodeI = 0
		for (node in runContext.nodes) {
			dotContent << "subgraph cluster_node${nodeI++} {\n"
			dotContent << "label=\"${node.label}\";\n"
			dotContent << 'style=dotted;\n'
			for (wrapper in runContext.wrappersByNode.get(node)) {
				dotContent << "${dotNodeNameByWrappers.get(wrapper)}[label=\"${wrapper.activity.name}\"];\n"
			}
			dotContent << '}\n'
		}
		dotContent << 'start[shape=cds];\n'
		for (wrapper in runContext.wrappers) {
			Set<Map.Entry<CipaActivityWrapper, Boolean>> dependencies = wrapper.dependencies
			if (dependencies.empty) {
				dotContent << "start -> ${dotNodeNameByWrappers.get(wrapper)};\n"
			} else {
				for (dependency in dependencies) {
					dotContent << "${dotNodeNameByWrappers.get(dependency.key)} -> ${dotNodeNameByWrappers.get(wrapper)}"
					if (!dependency.value.booleanValue()) {
						dotContent << ' [style=dashed]'
					}
					dotContent << ';\n'
				}
			}
		}
		dotContent << '}\n'
		return dotContent.toString()
	}

	@NonCPS
	private String transformSVG(String svgContent) {
		for (wrapperWithNodeName in dotNodeNameByWrappers) {
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

		return svgContent
	}

	private void updateGraph() {
		String svgContent = svgContentHolder.get()
		if (!svgContent) {
			if (produceSVGCalled.compareAndSet(false, true)) {
				try {
					svgContent = produceSVG()
					svgContentHolder.set(svgContent)
				} catch (err) {
					script.echo("SVG creation failed: ${err}")
				}
			}
		}
		if (svgContent) {
			svgContent = transformSVG(svgContent)
			try {
				script.setCustomBuildProperty('Activity-Graph', svgContent)
			} catch (err) {
				script.echo("setCustomBuildProperty failed: ${err}")
			}
		}
	}

}
