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

import hudson.model.Item
import hudson.model.ItemGroup
import hudson.model.Job

trait TmItemGroup<M extends ItemGroup> implements HasMock<M>, HasTmFactory, TmItemAttributes {

	final List<TmItem> tmItems = []

	TmJob getOrCreateTmJob(String fullQualifiedName) {
		return getOrCreateTmItem(fullQualifiedName, true) as TmJob
	}

	TmFolder getOrCreateTmFolder(String fullQualifiedName) {
		return getOrCreateTmItem(fullQualifiedName, false) as TmFolder
	}

	private TmItem getOrCreateTmItem(String fullQualifiedName, boolean job) {
		StringTokenizer tokens = new StringTokenizer(fullQualifiedName, '/')
		if (!tokens.hasMoreTokens()) {
			return null
		} else {
			TmItemGroup parent = this;
			while (true) {
				String name = tokens.nextToken()

				TmItem item = parent.getTmItem(name)
				if (item == null) {
					if (!tokens.hasMoreTokens() && job) {
						item = tmFactory.createTmJob(name, parent)
					} else {
						item = tmFactory.createTmFolder(name, parent)
					}
				}

				if (!tokens.hasMoreTokens()) {
					return item
				}

				if (!(item instanceof TmItemGroup)) {
					throw new RuntimeException("Cannot getOrCreate ${fullQualifiedName} as ${name} is not an ItemGroup")
				}

				parent = item
			}
		}
	}

	Collection getItems() {
		return tmItems.collect { it.mock }
	}

	TmItem getTmItem(String name) {
		return tmItems.find { it.name == name }
	}

	Item getItem(String name) {
		return getTmItem(name)?.mock as Item
	}

	TmItem getTmItemByFullName(String fullQualifiedName) {
		StringTokenizer tokens = new StringTokenizer(fullQualifiedName, '/')
		if (!tokens.hasMoreTokens()) {
			return null
		} else {
			TmItemGroup parent = this
			while (true) {
				TmItem item = parent.getTmItem(tokens.nextToken())
				if (!tokens.hasMoreTokens()) {
					return item
				}

				if (!(item instanceof TmItemGroup)) {
					return null
				}

				parent = item
			}
		}
	}

	Item getItemByFullName(String fullQualifiedName) {
		return getTmItemByFullName(fullQualifiedName)?.mock as Item
	}

	public <T extends Item> T getItemByFullName(String fullQualifiedName, Class<T> type) {
		def item = getTmItemByFullName(fullQualifiedName)?.mock
		return type.isInstance(item) ? type.cast(item) : null
	}

	Collection<TmJob> getAllTmJobs() {
		return tmItems.findAll { it instanceof TmJob }
	}

	Collection<? extends Job> getAllJobs() {
		return getAllTmJobs().collect { it.mock }
	}

}
