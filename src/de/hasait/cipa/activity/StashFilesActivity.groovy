/*
 * Copyright (C) 2020 by Sebastian Hasait (sebastian at hasait dot de)
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

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.artifactstore.CipaArtifactStore
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource

class StashFilesActivity extends AbstractCipaActivity implements CipaActivityWithStage {

	private final String name
	private final boolean withStage
	private final CipaResourceWithState<CipaFileResource> files
	private final String relDir
	private final CipaResourceWithState<CipaStashResource> stash

	private Set<String> fileIncludes = new LinkedHashSet<>()
	private Set<String> fileExcludes = new LinkedHashSet<>()
	private boolean useDefaultExcludes = true
	private boolean allowEmpty = false

	StashFilesActivity(Cipa cipa, String name, CipaResourceWithState<CipaFileResource> files, String subDir = null, boolean withStage = false) {
		super(cipa)

		this.name = name
		this.withStage = withStage

		this.files = files
		addRunRequiresRead(files)

		this.relDir = files.resource.path + (subDir ? '/' + subDir : '')

		String stashId = files.resource.node.label + '_' + name + '_' + relDir.replace('/', '_')
		this.stash = cipa.newStashResourceWithState(stashId, relDir, files.state)
		addRunProvides(stash)
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	@NonCPS
	boolean isWithStage() {
		return withStage
	}

	/**
	 * @param includes File include patterns.
	 * @return this
	 */
	@NonCPS
	StashFilesActivity include(String... includes) {
		fileIncludes.addAll(includes)
		return this
	}

	/**
	 * @param excludes File exclude patterns.
	 * @return this
	 */
	@NonCPS
	StashFilesActivity exclude(String... excludes) {
		fileExcludes.addAll(excludes)
		return this
	}

	@NonCPS
	StashFilesActivity disableDefaultExcludes() {
		useDefaultExcludes = false
		return this
	}

	@NonCPS
	StashFilesActivity enableAllowEmpty() {
		allowEmpty = true
		return this
	}

	@NonCPS
	CipaResourceWithState<CipaStashResource> getProvidedStash() {
		return stash
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		CipaArtifactStore cipaArtifactStore = cipa.findBean(CipaArtifactStore.class)

		script.echo("Stashing ${files}...")

		script.dir(relDir) {
			cipaArtifactStore.stash(runContext, stash.resource.id, fileIncludes, fileExcludes, useDefaultExcludes, allowEmpty)
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Stash ${files} as ${stash}"
	}

}
