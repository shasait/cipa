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

/**
 *
 */
@Deprecated
class PJobArgument<T> implements Serializable {

	final Class<T> valueType
	final String name
	final boolean required
	final T defaultValue
	final List<T> choices
	final boolean password
	final boolean retrieveFromParam
	final boolean retrieveExternally
	String description

	static PJobArgument<String> requiredText(String name, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newRequired(String.class, name, false, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<String> optionalText(String name, String defaultValue = null, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newOptional(String.class, name, defaultValue, false, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<String> requiredTextChoice(String name, List<String> choices, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newRequiredChoice(String.class, name, choices, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<String> optionalTextChoice(String name, List<String> choices, String defaultValue = null, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newOptionalChoice(String.class, name, choices, defaultValue, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<String> requiredPassword(String name, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newRequired(String.class, name, true, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<String> optionalPassword(String name, String defaultValue = null, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newOptional(String.class, name, defaultValue, true, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<Boolean> requiredBoolean(String name, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newRequired(Boolean.class, name, false, retrieveFromParam, retrieveExternally)
	}

	static PJobArgument<Boolean> optionalBoolean(String name, Boolean defaultValue = null, boolean retrieveFromParam = true, boolean retrieveExternally = true) {
		return newOptional(Boolean.class, name, defaultValue, false, retrieveFromParam, retrieveExternally)
	}

	private
	static <T> PJobArgument<T> newRequired(Class<T> valueType, String name, boolean password, boolean retrieveFromParam, boolean retrieveExternally) {
		return new PJobArgument<T>(valueType, name, true, null, null, password, retrieveFromParam, retrieveExternally)
	}

	private
	static <T> PJobArgument<T> newOptional(Class<T> valueType, String name, T defaultValue, boolean password, boolean retrieveFromParam, boolean retrieveExternally) {
		return new PJobArgument<T>(valueType, name, false, defaultValue, null, password, retrieveFromParam, retrieveExternally)
	}

	private
	static <T> PJobArgument<T> newRequiredChoice(Class<T> valueType, String name, List<T> choices, boolean retrieveFromParam, boolean retrieveExternally) {
		return new PJobArgument<T>(valueType, name, true, null, choices, false, retrieveFromParam, retrieveExternally)
	}

	private
	static <T> PJobArgument<T> newOptionalChoice(Class<T> valueType, String name, List<T> choices, T defaultValue, boolean retrieveFromParam, boolean retrieveExternally) {
		return new PJobArgument<T>(valueType, name, false, defaultValue, choices, false, retrieveFromParam, retrieveExternally)
	}

	private PJobArgument(
			Class<T> valueType,
			String name,
			boolean required,
			T defaultValue,
			List<T> choices,
			boolean password,
			boolean retrieveFromParam,
			boolean retrieveExternally) {
		this.valueType = valueType
		this.name = name
		this.required = required
		this.defaultValue = defaultValue
		this.choices = choices
		this.password = password
		this.retrieveFromParam = retrieveFromParam
		this.retrieveExternally = retrieveExternally
	}

	PJobArgument<T> description(String description) {
		this.description = description
		return this
	}

}
