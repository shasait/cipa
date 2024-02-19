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

package de.hasait.cipa.testsupport.model

import com.cloudbees.hudson.plugins.folder.Folder
import hudson.model.TopLevelItem
import hudson.util.DescribableList
import org.jenkinsci.plugins.workflow.job.WorkflowJob

class TmFolder extends TmItem<Folder> implements TmItemGroup<Folder> {

	final TmDescribableList tmProperties

	TmFolder(TmFactory tmFactory, TmItemGroup tmParent, String name) {
		super(Folder.class, tmFactory, tmParent, name)

		this.tmProperties = tmFactory.createTmDescribableList()
	}

	DescribableList getProperties() {
		return tmProperties.mock
	}

	public <T extends TopLevelItem> T createProject(Class<T> type, String name) {
		if (Folder.class.isAssignableFrom(type)) {
			return type.cast(tmFactory.createTmFolder(name, this).mock)
		}
		if (WorkflowJob.class.isAssignableFrom(type)) {
			return type.cast(tmFactory.createTmJob(name, this).mock)
		}
		return null
	}

}
