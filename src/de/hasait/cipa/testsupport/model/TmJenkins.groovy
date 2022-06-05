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

import hudson.ExtensionList
import hudson.model.Computer
import hudson.model.Node
import jenkins.model.Jenkins
import jenkins.model.Nodes

/**
 * <p>TestModel for {@link Jenkins}.</p>
 * <p>See {@link TmBase} for details.</p>
 */
class TmJenkins extends TmNode<Jenkins> implements TmItemGroup<Jenkins> {

	String rootUrl

	Map<Class, TmExtensionList> tmExtensionListMap = [:]

	Map<Node, Computer> computerMap = [:]
	Map<TmNode, TmComputer> tmComputers = [:]
	List<TmNode> tmNodes = []
	TmComputer masterTmComputer

	TmJenkins(TmFactory tmFactory, String hostName = 'master.example.org') {
		super(Jenkins.class, tmFactory, null, '', ['master'])

		rootUrl = 'https://' + hostName + '/'

		masterTmComputer = tmFactory.createTmComputer(this, hostName)
		addTmComputer(masterTmComputer)

		Jenkins.theInstance = mock
	}

	TmExtensionList createTmExtensionList(Class extensionType) {
		TmExtensionList simple = tmFactory.createTmExtensionList()
		tmExtensionListMap.put(extensionType, simple)
		return simple
	}

	TmComputer createTmNodeWithTmComputer(String nodeName, String hostName, String... labelNames) {
		List<String> labels = []
		labels.add(nodeName)
		labels.addAll(labelNames)

		TmNode<Node> tmNode = tmFactory.<Node> createTmNode(Node.class, this, nodeName, labels)
		TmComputer tmComputer = tmFactory.createTmComputer(tmNode, hostName)
		addTmComputer(tmComputer)
		return tmComputer
	}

	void addTmComputer(TmComputer tmComputer) {
		tmNodes.add(tmComputer.tmNode)
		tmComputers.put(tmComputer.tmNode, tmComputer)
		computerMap.put((Node) tmComputer.tmNode.mock, tmComputer.mock)
	}

	ExtensionList getExtensionList(Class extensionType) {
		if (tmExtensionListMap.containsKey(extensionType)) {
			return tmExtensionListMap.get(extensionType).mock
		}
		return createTmExtensionList(extensionType).mock
	}

	String getFullName() {
		return ''
	}

	String getUrl() {
		return ''
	}

	String getAbsoluteUrl() {
		return rootUrl
	}

	List<Nodes> getNodes() {
		return tmNodes.findAll { it != this }.collect { it.mock }
	}

	Computer getComputer(String name) {
		if (name == '(master)') {
			name = ''
		}

		for (TmComputer c : tmComputers.values()) {
			if (c.name == name) {
				return c.mock
			}
		}

		return null
	}

}
