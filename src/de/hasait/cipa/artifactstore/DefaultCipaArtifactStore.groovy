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

package de.hasait.cipa.artifactstore

import de.hasait.cipa.activity.AbstractCipaBean
import de.hasait.cipa.activity.CipaActivityRunContext

/**
 *
 */
class DefaultCipaArtifactStore extends AbstractCipaBean implements CipaArtifactStore {

	DefaultCipaArtifactStore(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	void archiveFile(CipaActivityRunContext runContext, String srcPath, String title) {
		script.archiveArtifacts(srcPath)
		runContext.publishFile(srcPath, title)
	}

	@Override
	void stash(CipaActivityRunContext runContext, String id, Set<String> includes, Set<String> excludes, boolean useDefaultExcludes, boolean allowEmpty) {
		script.stash(id, includes, excludes, useDefaultExcludes, allowEmpty)
	}

	@Override
	void unstash(CipaActivityRunContext runContext, String id) {
		script.unstash(id)
	}

}
