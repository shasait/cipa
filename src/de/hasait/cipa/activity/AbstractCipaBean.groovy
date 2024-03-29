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

package de.hasait.cipa.activity


import de.hasait.cipa.Cipa
import de.hasait.cipa.PScript

/**
 *
 */
abstract class AbstractCipaBean implements Serializable {

	protected final Cipa cipa
	protected final String cipaBeanName

	protected final PScript script
	protected final rawScript

	AbstractCipaBean(rawScriptOrCipa, boolean addToCipa) {
		this(rawScriptOrCipa, null, addToCipa)
	}

	AbstractCipaBean(rawScriptOrCipa, String cipaBeanName = null, boolean addToCipa = true) {
		if (rawScriptOrCipa instanceof Cipa) {
			this.cipa = rawScriptOrCipa
		} else {
			this.cipa = Cipa.getOrCreate(rawScriptOrCipa)
		}
		this.cipaBeanName = cipaBeanName

		this.script = cipa.findBean(PScript.class)
		this.rawScript = script.rawScript

		if (addToCipa) {
			cipa.addBean(this, cipaBeanName)
		}
	}

}
