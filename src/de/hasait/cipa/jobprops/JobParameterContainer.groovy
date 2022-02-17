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

package de.hasait.cipa.jobprops

import hudson.model.ParameterDefinition

interface JobParameterContainer {

	public <T> void addArgument(PJobArgument<T> argument)

	void addStringParameter(String name, String defaultValue, String description)

	void addStringParameter(String name, String defaultValue, String description, String regex, String failedValidationMessage)

	void addBooleanParameter(String name, boolean defaultValue, String description)

	void addBooleanChoiceParameter(String name, Boolean defaultValue, String description)

	void addPasswordParameter(String name, String description)

	void addChoiceParameter(String name, List<String> choices, String description)

	void addCustomParameter(ParameterDefinition parameter)

}
