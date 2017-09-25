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
import de.hasait.cipa.resource.CipaResourceWithState

class CreateActivityGraphActivity implements CipaInit, CipaActivity, Serializable {

	private final Cipa cipa
	private final CipaNode node
	private final String name

	private PScript script

	CreateActivityGraphActivity(Cipa cipa, CipaNode node, String name = 'CreateActivityGraph') {
		this.cipa = cipa
		this.node = node
		this.name = name

		cipa.addBean(this)
	}

	@Override
	@NonCPS
	String getName() {
		return name
	}

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresRead() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunRequiresWrite() {
		return []
	}

	@Override
	@NonCPS
	Set<CipaResourceWithState<?>> getRunProvides() {
		return []
	}

	@Override
	@NonCPS
	CipaNode getNode() {
		return node
	}

	@Override
	void prepareNode() {
		// nop
	}

	@Override
	void runActivity() {
		script.echo("Creating activity graph...")

		script.dir(name) {
			String basename = 'activities-graph'
			script.writeFile("${basename}.dot", cipa.dotContent)
			script.sh("( test -x /usr/bin/dot && /usr/bin/dot -Tpng ${basename}.dot > ${basename}.png && /usr/bin/dot -Tsvg ${basename}.dot > ${basename}.svg ) || true")
			script.archiveArtifacts("${basename}.*")
		}
	}

	@Override
	@NonCPS
	String toString() {
		return "Create activity graph"
	}

}
