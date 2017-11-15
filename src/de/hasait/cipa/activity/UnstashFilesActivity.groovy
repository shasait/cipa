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

class UnstashFilesActivity implements CipaInit, CipaActivity, CipaActivityWithStage, Serializable {

	private final Cipa cipa
	private final String name
	private final boolean withStage
	private final CipaResourceWithState<CipaStashResource> stash
	private final CipaResourceWithState<CipaFileResource> files

	private PScript script

	UnstashFilesActivity(Cipa cipa, String name, CipaResourceWithState<CipaStashResource> stash, CipaNode node, String relDir = null, boolean withStage = false) {
		this.cipa = cipa
		this.name = name
		this.withStage = withStage
		this.stash = stash

		this.files = cipa.newFileResourceWithState(node, relDir ?: stash.resource.srcRelDir, stash.state)

		cipa.addBean(this)
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

	@NonCPS
	CipaResourceWithState<CipaFileResource> getProvidedFiles() {
		return files
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresRead() {
		return [stash]
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresWrite() {
		return []
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
		script.echo("Deleting ${files}...")

		script.dir(files.resource.path) {
			script.deleteDir()
		}
	}

	@Override
	void runActivity(CipaActivityRunContext runContext) {
		script.echo("Unstashing ${stash} into ${files.resource}...")

		script.dir(files.resource.path) {
			script.unstash(stash.resource.id)
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Unstash ${stash} into ${files.resource}"
	}

}
