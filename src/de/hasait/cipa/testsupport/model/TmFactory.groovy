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

import hudson.model.Node

class TmFactory {

	TmJenkins createTmJenkins() {
		return new TmJenkins(this)
	}

	TmRawScript createTmRawScript() {
		return new TmRawScript()
	}

	TmJob createTmJob(String name, TmItemGroup tmParent) {
		return new TmJob(this, tmParent, name)
	}

	TmFolder createTmFolder(String name, TmItemGroup tmParent) {
		return new TmFolder(this, tmParent, name)
	}

	TmRun createTmRun(TmJob tmJob, int number) {
		return new TmRun(this, tmJob, number)
	}

	public <M extends Node> TmNode<M> createTmNode(Class<M> mockClass, TmJenkins tmJenkins, String nodeName, List<String> labels) {
		return new TmNode(mockClass, this, tmJenkins, nodeName, labels)
	}

	TmComputer createTmComputer(TmNode tmNode, String hostName) {
		return new TmComputer(this, tmNode, hostName)
	}

	TmExtensionList createTmExtensionList() {
		return new TmExtensionList(this)
	}

	TmDescribableList createTmDescribableList() {
		return new TmDescribableList(this)
	}

}
