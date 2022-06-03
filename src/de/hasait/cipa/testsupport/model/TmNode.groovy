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

import hudson.model.Computer
import hudson.model.Node
import hudson.model.labels.LabelAtom

class TmNode<M extends Node> extends MockWrapper<M> {

	TmJenkins tmJenkins

	String nodeName

	Set<LabelAtom> assignedLabels = new LinkedHashSet<>()

	TmNode(Class<M> mockClass, TmJenkins tmJenkins, String nodeName, List<String> labels) {
		super(mockClass)

		if (this instanceof TmJenkins) {
			this.tmJenkins = (TmJenkins) this
		} else {
			this.tmJenkins = tmJenkins
		}


		this.nodeName = nodeName
		for (labelName in labels) {
			LabelAtom label = new LabelAtom(labelName)
			assignedLabels.add(label)
		}
	}

	Computer toComputer() {
		return tmJenkins.computerMap.get(mock)
	}

}
