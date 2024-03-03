/*
 * Copyright (C) 2024 by Sebastian Hasait (sebastian at hasait dot de)
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
import org.jenkinsci.plugins.custombuildproperties.CustomBuildPropertiesAction

class UpdateGraphAroundActivity extends AbstractCipaAroundActivity implements CipaAfterActivities, Serializable {

	static final int AROUND_ACTIVITY_ORDER = 1000

	static final String ACTIVITY_GRAPH_CBP_KEY = 'Activity-Graph'

	private final Map<CipaActivityInfo, String> dotNodeNameByActivityInfo = new HashMap<>()

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
	void runAroundActivity(CipaActivityInfo activityInfo, Closure<?> next) {
		updateGraph()
		next.call()
	}

	@Override
	void afterActivityFinished(CipaActivityInfo activityInfo) {
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
		StringBuilder dotContent = new StringBuilder()
		dotContent << '\n'
		dotContent << 'digraph pipeline {\n'
		dotContent << 'rankdir="LR";\n'
		dotContent << 'newrank=true;\n'
		dotContent << 'splines=ortho;\n'
		dotContent << 'node[shape=box];\n'
		int activityI = 0
		for (activityInfo in cipa.activityInfos) {
			dotNodeNameByActivityInfo.put(activityInfo, "a${activityI++}")
		}

		int nodeI = 0
		cipa.activityInfosByNode.each { node, activityInfos ->
			dotContent << "subgraph cluster_node${nodeI++} {\n"
			dotContent << "label=\"${node.label}\";\n"
			dotContent << 'style=dotted;\n'
			for (activityInfo in activityInfos) {
				dotContent << "${dotNodeNameByActivityInfo.get(activityInfo)}[label=\"${activityInfo.activity.name}\"];\n"
			}
			dotContent << '}\n'
		}
		dotContent << 'start[shape=cds];\n'
		for (activityInfo in cipa.activityInfos) {
			Set<Map.Entry<CipaActivityInfo, Boolean>> dependencies = activityInfo.dependencies
			if (dependencies.empty) {
				dotContent << "start -> ${dotNodeNameByActivityInfo.get(activityInfo)};\n"
			} else {
				for (dependency in dependencies) {
					dotContent << "${dotNodeNameByActivityInfo.get(dependency.key)} -> ${dotNodeNameByActivityInfo.get(activityInfo)}"
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
		for (activityInfoWithNodeName in dotNodeNameByActivityInfo) {
			CipaActivityInfo activityInfo = activityInfoWithNodeName.key
			String nodeName = activityInfoWithNodeName.value
			boolean running = activityInfo.running
			boolean failed = activityInfo.failed
			boolean depsFailed = activityInfo.failedDependencies
			boolean done = activityInfo.done
			boolean stable = activityInfo.testSummary.stable
			String fill = depsFailed ? "gray" : (failed ? "red" : (done ? (stable ? "lightgreen" : "yellow") : "none"))
			String stroke = running ? "blue" : "black"
			String title = activityInfo.activity.name.replace('<', '$lt;').replace('>', '$gt;')
			svgContent = Pattern.compile("(<title>)${nodeName}(</title>\\s+<[a-z]+ fill=\")[^\"]+(\" stroke=\")[^\"]+(\")").matcher(svgContent).replaceFirst("\$1${title}\$2${fill}\$3${stroke}\$4")
		}

		return svgContent
	}

	private void updateGraph() {
		String svgContent = svgContentHolder.get()
		if (svgContent == null) {
			if (produceSVGCalled.compareAndSet(false, true)) {
				try {
					svgContent = produceSVG()
					svgContentHolder.set(svgContent)
				} catch (err) {
					script.echo("SVG creation failed: ${err}")
				}
				try {
					// optional logic for custom-build-properties-plugin >= 2.90
					String cbpSanizizerPrefix = CustomBuildPropertiesAction.class.getDeclaredField('CBP_SANITIZER_PREFIX').get(null)
					String cbpInternalSanizizer = CustomBuildPropertiesAction.class.getDeclaredField('CBP_INTERNAL_SANITIZER').get(null)
					script.setCustomBuildProperty(cbpSanizizerPrefix + 'Key_' + ACTIVITY_GRAPH_CBP_KEY + '_Value', cbpInternalSanizizer)
				} catch (err) {
					// ignored
				}
			}
		}
		if (svgContent != null) {
			svgContent = transformSVG(svgContent)
			try {
				script.setCustomBuildProperty(ACTIVITY_GRAPH_CBP_KEY, svgContent)
			} catch (err) {
				script.echo("setCustomBuildProperty failed: ${err}")
			}
		}
	}

}
