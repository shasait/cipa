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
import de.hasait.cipa.Script
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource

class UnstashFilesActivity implements CipaInit, CipaActivity, Serializable {

	private final Cipa cipa
	private final String name
	private final CipaResourceWithState<CipaStashResource> stash
	private final CipaResourceWithState<CipaFileResource> files

	private Script script
	private def rawScript

	UnstashFilesActivity(Cipa cipa, String name, CipaResourceWithState<CipaStashResource> stash, CipaNode node, String relDir = null) {
		this.cipa = cipa
		this.name = name
		this.stash = stash

		this.files = cipa.newFileResourceWithState(node, relDir ?: stash.resource.srcRelDir, stash.state)

		cipa.addBean(this)
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> getProvidedFiles() {
		return files
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(Script.class)
		rawScript = script.rawScript
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequires() {
		return [stash]
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunProvides() {
		return [files]
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return files.resource.node
	}

	@Override
	void prepareNode() {
		script.echo('Deleting ${files}...')

		script.dir(files.resource.path) {
			script.deleteDir()
		}
	}

	@Override
	void runActivity() {
		script.echo("Unstashing ${stash} into ${files}...")

		script.dir(files.resource.path) {
			rawScript.unstash(name: stash.resource.id)
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Unstash ${stash} into ${files}"
	}

}
