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

class TmJenkins extends TmNode<Jenkins> implements TmItemGroup<Jenkins> {

	String rootUrl = 'https://jenkins.example.org/'

	Map<Class, TmExtensionList> tmExtensionListMap = [:]

	Map<Node, Computer> computerMap = [:]
	Map<TmNode, TmComputer> tmComputers = [:]
	List<TmNode> tmNodes = []
	TmComputer masterTmComputer

	TmJenkins() {
		super(Jenkins.class, null, '', ['master'])

		masterTmComputer = new TmComputer(this, 'jenkins.example.org')
		addTmNodeWithComputer(this, masterTmComputer)

		Jenkins.theInstance = mock
	}

	ExtensionList getExtensionList(Class extensionType) {
		if (tmExtensionListMap.containsKey(extensionType)) {
			return tmExtensionListMap.get(extensionType).mock
		}
		return createTmExtensionList(extensionType).mock
	}

	TmExtensionList createTmExtensionList(Class extensionType) {
		TmExtensionList simple = new TmExtensionList()
		tmExtensionListMap.put(extensionType, simple)
		return simple
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

	void createTmNodeWithComputer(String nodeName, String hostName, String... labelNames) {
		List<String> labels = []
		labels.add(nodeName)
		labels.addAll(labelNames)

		TmNode<Node> tmNode = new TmNode<>(Node.class, this, nodeName, labels)
		TmComputer tmComputer = new TmComputer(tmNode, hostName)
		addTmNodeWithComputer(tmNode, tmComputer)
	}

	void addTmNodeWithComputer(TmNode tmNode, TmComputer tmComputer) {
		tmNodes.add(tmNode)
		tmComputers.put(tmNode, tmComputer)
		computerMap.put((Node) tmNode.mock, tmComputer.mock)
	}

}
