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

import javax.annotation.Nonnull

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.PScript
import hudson.model.ParameterDefinition

/**
 * Pipeline Job Properties Manager.
 * This manager can be used without core Cipa.
 * When using Cipa you should not use this class directly but {@link JobParameterContribution} and {@link JobPropertiesContribution} instead.
 * It especially manages Job Parameters:
 * They can be added using the various add methods.
 * After {@link #applyJobProperties} has been called they are configured and the values can be retrieved.
 * Parameter values will be obtained not only from the parameters itself but additionally from a json block in the job description and/or parent folder descriptions.
 */
class PJobPropertiesManager implements JobParameterContainer, JobParameterValues, JobPropertiesContainer {

	private static final String BC_TRUE_VALUE = '(X)'
	private static final String BC_FALSE_VALUE = '( )'

	private final PScript script
	private final def rawScript

	private final Map<String, PJobArgument<?>> arguments = [:]
	private final List parameters = []
	private final List pipelineTriggers = []
	private def buildDiscarder
	private boolean rebuildSettingsAutoRebuild = false
	private boolean rebuildSettingsRebuildDisabled = false
	private final List customJobProperties = []

	private Map<String, Object> descriptionValues

	PJobPropertiesManager(PScript script) {
		this.script = script
		this.rawScript = script.rawScript
	}

	@Override
	@NonCPS
	final <T> void addArgument(PJobArgument<T> argument) {
		Objects.requireNonNull(argument)
		PJobArgument<?> oldArgument = arguments.put(argument.name, argument)
		if (oldArgument) {
			throw new IllegalArgumentException("Name collision for argument: ${argument} vs. ${oldArgument}")
		}

		if (argument.retrieveFromParam) {
			String description = argument.description

			boolean optionalParam = !argument.required || argument.retrieveFromDescriptions
			description += optionalParam ? ' (optional)' : ' (required)'

			T paramDefaultValue = argument.retrieveFromDescriptions ? null : argument.defaultValue
			if (argument.retrieveFromDescriptions && argument.defaultValue) {
				description += ' (default: ' + argument.defaultValue + ')'
			}

			if (argument.password) {
				if (argument.valueType == String.class) {
					// password
					def parameter = rawScript.password(name: argument.name, defaultValue: paramDefaultValue, description: description)
					parameters.add(parameter)
				} else {
					throw new RuntimeException("Unsupported argument: ${argument}")
				}
			} else if (argument.choices) {
				if (argument.valueType == String.class) {
					// string choice
					List<String> choices = new ArrayList<>()
					if (optionalParam) {
						choices.add('')
					}
					choices.addAll(argument.choices)
					def parameter = rawScript.choice(name: argument.name, choices: choices.join('\n'), description: description)
					parameters.add(parameter)
				} else {
					throw new RuntimeException("Unsupported argument: ${argument}")
				}
			} else if (argument.valueType == String.class) {
				// single string
				def parameter = rawScript.string(name: argument.name, defaultValue: paramDefaultValue, description: description)
				parameters.add(parameter)
			} else if (argument.valueType == Boolean.class) {
				// single boolean
				if (optionalParam) {
					List<String> choices = []
					if (paramDefaultValue != null) {
						boolean defaultBoolean = ((Boolean) paramDefaultValue).booleanValue()
						choices.add(defaultBoolean ? BC_TRUE_VALUE : BC_FALSE_VALUE)
						choices.add(!defaultBoolean ? BC_TRUE_VALUE : BC_FALSE_VALUE)
						choices.add('')
					} else {
						choices.add('')
						choices.add(BC_TRUE_VALUE)
						choices.add(BC_FALSE_VALUE)
					}
					def parameter = rawScript.choice(name: argument.name, choices: choices.join('\n'), description: description)
					parameters.add(parameter)
				} else {
					def parameter = rawScript.booleanParam(name: argument.name, defaultValue: paramDefaultValue, description: description)
					parameters.add(parameter)
				}
			} else {
				throw new RuntimeException("Unsupported argument: ${argument}")
			}
		}
	}

	@Override
	@NonCPS
	final <T> T retrieveArgumentValue(PJobArgument<T> argument) {
		Objects.requireNonNull(argument)
		String name = argument.name
		if (!arguments.containsKey(name)) {
			throw new IllegalStateException("Argument ${name} not declared")
		}

		T result = null

		if (argument.retrieveFromParam) {
			boolean optionalParam = !argument.required || argument.retrieveFromDescriptions

			if (argument.valueType == Boolean.class) {
				if (optionalParam) {
					String paramValue = readParam(name)
					if (paramValue) {
						result = BC_TRUE_VALUE.equals(paramValue)
					}
				} else {
					result = readBooleanParam(name)
				}
			} else {
				result = readParam(name) ?: null
			}
		}

		if (result == null && argument.retrieveFromDescriptions) {
			result = descriptionValues[name] ?: null
		}

		if (result == null) {
			result = argument.defaultValue
		}

		if (result == null && argument.required) {
			throw new RuntimeException("Argument {name} missing")
		}

		return result
	}

	@Override
	@NonCPS
	final void addStringParameter(String name, String defaultValue, String description) {
		def parameter = rawScript.string(name: name, defaultValue: defaultValue, description: description)
		parameters.add(parameter)
	}

	@Override
	@NonCPS
	final void addStringParameter(String name, String defaultValue, String description, String regex, String failedValidationMessage) {
		def parameter = rawScript.validatingString(name: name, defaultValue: defaultValue, description: description, regex: regex, failedValidationMessage: failedValidationMessage)
		parameters.add(parameter)
	}

	/**
	 * Add a native boolean parameter. It cannot be null and therefore the environment fallback is not working for it - see {@link #addBooleanChoiceParameter}.
	 */
	@Override
	@NonCPS
	final void addBooleanParameter(String name, boolean defaultValue, String description) {
		def parameter = rawScript.booleanParam(name: name, defaultValue: defaultValue, description: description)
		parameters.add(parameter)
	}

	@Override
	@NonCPS
	final void addBooleanChoiceParameter(String name, Boolean defaultValue, String description) {
		List<String> choices = []
		if (defaultValue != null) {
			choices.add(defaultValue ? BC_TRUE_VALUE : BC_FALSE_VALUE)
			choices.add(!defaultValue ? BC_TRUE_VALUE : BC_FALSE_VALUE)
			choices.add('')
		} else {
			choices.add('')
			choices.add(BC_TRUE_VALUE)
			choices.add(BC_FALSE_VALUE)
		}
		addChoiceParameter(name, choices, description)
	}

	@Override
	@NonCPS
	final void addPasswordParameter(String name, String description) {
		def parameter = rawScript.password(name: name, defaultValue: '', description: description)
		parameters.add(parameter)
	}

	@Override
	@NonCPS
	final void addChoiceParameter(String name, List<String> choices, String description) {
		def parameter = rawScript.choice(name: name, choices: choices.join('\n'), description: description)
		parameters.add(parameter)
	}

	@Override
	@NonCPS
	void addCustomParameter(ParameterDefinition parameter) {
		parameters.add(parameter)
	}

	@NonCPS
	final void addPipelineTrigger(def trigger) {
		pipelineTriggers.add(trigger)
	}

	@NonCPS
	final void addCustomJobProperty(def customJobProperty) {
		customJobProperties.add(customJobProperty)
	}

	@NonCPS
	final Object retrieveRequiredValue(String name) {
		return retrieveValueFromParametersOrEnvironment(name)
	}

	@NonCPS
	final Object retrieveOptionalValue(String name, Object defaultValue) {
		return retrieveValueFromParametersOrEnvironment(name, false) ?: defaultValue
	}

	/**
	 * Try to get value from params, if not set fallback to descriptionValues.
	 * If required and both are null throw an exception otherwise return null.
	 */
	@NonCPS
	final Object retrieveValueFromParametersOrEnvironment(String name, boolean required = true) {
		def value = readParam(name) ?: descriptionValues[name] ?: null
		if (value || !required) {
			return value
		}
		throw new RuntimeException("${name} is neither in params nor in descriptionValues")
	}

	@NonCPS
	final String retrieveOptionalStringParameterValue(String name, String defaultValue) {
		return (String) retrieveValueFromParametersOrEnvironment(name, false) ?: defaultValue
	}

	@NonCPS
	final String retrieveRequiredStringParameterValue(String name) {
		return (String) retrieveValueFromParametersOrEnvironment(name)
	}

	@NonCPS
	final boolean retrieveOptionalBooleanChoiceParameterValue(String name, boolean defaultValue) {
		return retrieveOptionalStringParameterValue(name, defaultValue ? BC_TRUE_VALUE : BC_FALSE_VALUE) == BC_TRUE_VALUE
	}

	@NonCPS
	final boolean retrieveRequiredBooleanChoiceParameterValue(String name) {
		return retrieveRequiredStringParameterValue(name) == BC_TRUE_VALUE
	}

	@NonCPS
	final boolean retrieveBooleanParameterValue(String name) {
		return readBooleanParam(name).booleanValue()
	}

	@NonCPS
	final void setBuildDiscarder(def buildDiscarder) {
		this.buildDiscarder = buildDiscarder
	}

	@NonCPS
	final void setRebuildSettingsAutoRebuild(boolean autoRebuild) {
		this.rebuildSettingsAutoRebuild = autoRebuild
	}

	@NonCPS
	final void setRebuildSettingsRebuildDisabled(boolean rebuildDisabled) {
		this.rebuildSettingsRebuildDisabled = rebuildDisabled
	}

	final void applyJobProperties() {
		List jobProperties = []

		if (parameters) {
			jobProperties.add(rawScript.parameters(parameters))
		}

		def buildDiscarderWithDefaulting
		if (buildDiscarder) {
			buildDiscarderWithDefaulting = buildDiscarder
		} else {
			buildDiscarderWithDefaulting = rawScript.logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20')
		}
		jobProperties.add(rawScript.buildDiscarder(buildDiscarderWithDefaulting))

		if (pipelineTriggers) {
			jobProperties.add(rawScript.pipelineTriggers(pipelineTriggers))
		}

		jobProperties.add([$class: 'RebuildSettings', autoRebuild: rebuildSettingsAutoRebuild, rebuildDisabled: rebuildSettingsRebuildDisabled])

		if (customJobProperties) {
			jobProperties.addAll(customJobProperties)
		}

		rawScript.properties(jobProperties)

		descriptionValues = script.determineParametersFromDescriptionValues()
	}

	@NonCPS
	private Object readParam(String name) {
		Objects.requireNonNull(name)

		// TODO remove fallback to P_ after projects migrated
		return rawScript.params['P_' + name] ?: rawScript.params[name]
	}

	@NonCPS
	@Nonnull
	private Boolean readBooleanParam(String name) {
		Objects.requireNonNull(name)

		// TODO remove fallback to P_ after projects migrated
		Boolean value = (Boolean) rawScript.params['P_' + name]
		value = value != null ? value : rawScript.params[name]
		if (value == null) {
			// cannot be null
			throw new RuntimeException("${name} is not a param (yet?)")
		}
		return value
	}

}
