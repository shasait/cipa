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
import jenkins.model.Jenkins

class TmJenkins extends MockWrapper<Jenkins> implements TmItemGroup<Jenkins> {

	String rootUrl = 'https://jenkins.example.org/'

	Map<Class, TmExtensionList> tmExtensionListMap = [:]

	TmJenkins() {
		super(Jenkins.class)

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

}
