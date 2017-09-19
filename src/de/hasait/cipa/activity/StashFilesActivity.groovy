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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaNode
import de.hasait.cipa.PScript
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource

class StashFilesActivity implements CipaInit, CipaActivity, Serializable {

	private final Cipa cipa
	private final String name
	private final CipaResourceWithState<CipaFileResource> files
	private final String subDir
	private final CipaResourceWithState<CipaStashResource> stash

	private PScript script

	private Set<String> fileIncludes = new LinkedHashSet<>()
	private Set<String> fileExcludes = new LinkedHashSet<>()

	@NonCPS
	private static String relDir(CipaResourceWithState<CipaFileResource> files, String subDir) {
		return files.resource.path + (subDir ? '/' + subDir : '')
	}

	StashFilesActivity(Cipa cipa, String name, CipaResourceWithState<CipaFileResource> files, String subDir = '') {
		this.cipa = cipa
		this.name = name
		this.files = files
		this.subDir = subDir

		String relDir = relDir(files, subDir)
		String stashId = files.resource.node.label + '_' + relDir.replace('/', '_')
		this.stash = cipa.newStashResourceWithState(stashId, relDir, files.state)

		cipa.addBean(this)
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
	CipaResourceWithState<CipaStashResource> getProvidedStash() {
		return stash
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresRead() {
		return [files]
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresWrite() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunProvides() {
		return [stash]
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return files.resource.node
	}

	@Override
	void prepareNode() {
		// nop
	}

	@Override
	void runActivity() {
		script.echo("Stashing ${files}...")

		script.dir(relDir(files, subDir)) {
			script.stash(stash.resource.id, fileIncludes, fileExcludes)
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Stash ${files} as ${stash}"
	}

}
