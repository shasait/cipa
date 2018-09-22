/*
 * Copyright (C) 2018 by Sebastian Hasait (sebastian at hasait dot de)
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
import de.hasait.cipa.CipaInit
import de.hasait.cipa.PScript

/**
 *
 */
abstract class AbstractCipaBean implements CipaInit, Serializable {

	protected PScript script
	protected rawScript

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript
	}

}
