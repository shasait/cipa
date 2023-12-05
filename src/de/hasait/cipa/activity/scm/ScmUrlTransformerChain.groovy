/*
 * Copyright (C) 2023 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.activity.scm

import java.util.concurrent.atomic.AtomicReference

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.activity.AbstractCipaBean

class ScmUrlTransformerChain extends AbstractCipaBean implements ScmUrlTransformer {

	private AtomicReference<List<ScmUrlTransformer>> transformerListHolder = new AtomicReference<>()

	ScmUrlTransformerChain(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@NonCPS
	String transformScmUrl(String scmUrl) {
		if (transformerListHolder.get() == null) {
			List<ScmUrlTransformer> transformerList = cipa.findBeansAsList(ScmUrlTransformer.class)
			transformerList.remove(this)
			transformerListHolder.compareAndSet(null, transformerList)
		}

		String result = scmUrl

		List<ScmUrlTransformer> transformerList = transformerListHolder.get()
		if (!transformerList.empty) {
			for (ScmUrlTransformer transformer in transformerList) {
				result = transformer.transformScmUrl(result)
			}
		}

		return result
	}

}
