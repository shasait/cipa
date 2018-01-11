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

package de.hasait.cipa

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.resource.CipaResourceWithState

class CipaRunContext implements Serializable {

	final List<CipaActivityWrapper> wrappers = new ArrayList<>()
	final List<CipaNode> nodes = new ArrayList<>()
	final Map<CipaNode, List<CipaActivityWrapper>> wrappersByNode = new HashMap<>()
	final Map<CipaActivityWrapper, String> dotNodeNameByWrappers = new HashMap<>()
	final String dotContent

	CipaRunContext(Cipa cipa) {
		init(cipa)
		dotContent = produceDot()
	}

	@NonCPS
	private void init(Cipa cipa) {
		nodes.addAll(cipa.findBeans(CipaNode.class))
		PScript script = cipa.findBean(PScript.class)
		Set<CipaResourceWithState<?>> resources = cipa.findBeans(CipaResourceWithState.class)
		Set<CipaActivity> activities = cipa.findBeans(CipaActivity.class)
		List<CipaAroundActivity> aroundActivities = cipa.findBeansAsList(CipaAroundActivity.class)
		aroundActivities.sort({ it.runAroundActivityOrder })

		for (node in nodes) {
			wrappersByNode.put(node, new ArrayList<>())
		}
		Map<CipaResourceWithState<?>, List<CipaActivityWrapper>> activitiesRequiresRead = new HashMap<>()
		Map<CipaResourceWithState<?>, List<CipaActivityWrapper>> activitiesRequiresWrite = new HashMap<>()
		Map<CipaResourceWithState<?>, List<CipaActivityWrapper>> activitiesProvides = new HashMap<>()
		for (activity in activities) {
			CipaNode node = activity.node
			if (!wrappersByNode.containsKey(node)) {
				throw new IllegalStateException("${node} unknown - either create with cipa.newNode or register with addBean!")
			}
			CipaActivityWrapper wrapper = new CipaActivityWrapper(cipa, script, activity, aroundActivities)
			wrappers.add(wrapper)
			wrappersByNode.get(node).add(wrapper)
			for (requires in activity.runRequiresRead) {
				if (!resources.contains(requires)) {
					throw new IllegalStateException("${requires} unknown - either create with cipa.new* or register with addBean!")
				}
				if (!activitiesRequiresRead.containsKey(requires)) {
					activitiesRequiresRead.put(requires, new ArrayList<>())
				}
				activitiesRequiresRead.get(requires).add(wrapper)
			}
			for (requires in activity.runRequiresWrite) {
				if (!resources.contains(requires)) {
					throw new IllegalStateException("${requires} unknown - either create with cipa.new* or register with addBean!")
				}
				if (!activitiesRequiresWrite.containsKey(requires)) {
					activitiesRequiresWrite.put(requires, new ArrayList<>())
				}
				activitiesRequiresWrite.get(requires).add(wrapper)
			}
			for (provides in activity.runProvides) {
				if (!resources.contains(provides)) {
					throw new IllegalStateException("${provides} unknown - either create with cipa.new* or register with addBean!")
				}
				if (!activitiesProvides.containsKey(provides)) {
					activitiesProvides.put(provides, new ArrayList<>())
				}
				activitiesProvides.get(provides).add(wrapper)
			}
		}
		for (requires in activitiesRequiresRead) {
			if (!activitiesProvides.containsKey(requires.key)) {
				throw new IllegalArgumentException("Required ${requires.key} not provided by any activity!")
			}
			List<CipaActivityWrapper> providesWrappers = activitiesProvides.get(requires.key)
			for (requiresWrapper in requires.value) {
				for (providesWrapper in providesWrappers) {
					requiresWrapper.addDependency(providesWrapper)
				}
			}
		}
		for (requires in activitiesRequiresWrite) {
			if (!activitiesProvides.containsKey(requires.key)) {
				throw new IllegalArgumentException("Required ${requires.key} not provided by any activity!")
			}
			List<CipaActivityWrapper> providesWrappers = activitiesProvides.get(requires.key)
			List<CipaActivityWrapper> readers = activitiesRequiresRead.get(requires.key)
			CipaActivityWrapper lastWriter = null
			for (requiresWrapper in requires.value) {
				// Chain writers
				if (lastWriter) {
					requiresWrapper.addDependency(lastWriter, false)
				} else if (readers) {
					// Execute all readers before any writer, if there was already a writer we only depend on it
					for (reader in readers) {
						requiresWrapper.addDependency(reader, false)
					}
				}
				for (providesWrapper in providesWrappers) {
					requiresWrapper.addDependency(providesWrapper)
				}

				lastWriter = requiresWrapper
			}
		}
	}

	@NonCPS
	private String produceDot() {
		StringBuilder dotContent = new StringBuilder()
		dotContent << '\n'
		dotContent << 'digraph pipeline {\n'
		dotContent << 'rankdir="LR";\n'
		dotContent << 'node[shape=box];\n'
		int activityI = 0
		for (wrapper in wrappers) {
			dotNodeNameByWrappers.put(wrapper, "a${activityI++}")
		}
		int nodeI = 0
		for (node in nodes) {
			dotContent << "subgraph cluster_node${nodeI++} {\n"
			dotContent << "label=\"${node.label}\";\n"
			dotContent << 'style=dotted;\n'
			for (wrapper in wrappersByNode.get(node)) {
				dotContent << "${dotNodeNameByWrappers.get(wrapper)}[label=\"${wrapper.activity.name}\"];\n"
			}
			dotContent << '}\n'
		}
		dotContent << 'start[shape=cds];\n'
		for (wrapper in wrappers) {
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
	boolean getAllFinished() {
		for (wrapper in wrappers) {
			if (!wrapper.finishedDate) {
				return false
			}
		}
		return true
	}

}
