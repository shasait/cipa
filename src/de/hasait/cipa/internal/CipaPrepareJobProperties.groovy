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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaInit
import de.hasait.cipa.CipaPrepare
import de.hasait.cipa.JobParameterContainer
import de.hasait.cipa.JobParameterContribution
import de.hasait.cipa.JobParameterValues
import de.hasait.cipa.JobPropertiesContainer
import de.hasait.cipa.JobPropertiesContribution
import de.hasait.cipa.PScript

class CipaPrepareJobProperties implements CipaInit, CipaPrepare, JobParameterContainer, JobParameterValues, JobPropertiesContainer, Serializable {

	private static final String PARAM_PREFIX = 'P_'

	private PScript script
	private def rawScript

	private final List parameters = []
	private final List pipelineTriggers = []
	private def buildDiscarder
	private boolean rebuildSettingsAutoRebuild = false
	private boolean rebuildSettingsRebuildDisabled = false
	private final List customJobProperties = []

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript
	}

	@Override
	@NonCPS
	int getPrepareCipaOrder() {
		return 2000
	}

	@Override
	void prepareCipa(Cipa cipa) {
		List<JobParameterContribution> parameterContributions = cipa.findBeansAsList(JobParameterContribution.class)

		script.echo("Collecting parameters via ${JobParameterContribution.class.simpleName}s...")
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.contributeParameters(this)
		}

		List<JobPropertiesContribution> propertiesContributions = cipa.findBeansAsList(JobPropertiesContribution.class)

		script.echo("Collecting job properties via ${JobPropertiesContribution.class.simpleName}s...")
		for (JobPropertiesContribution propertiesContribution in propertiesContributions) {
			propertiesContribution.contributeJobProperties(this)
		}

		script.echo('Updating job properties...')
		updateJobProperties()

		script.echo('Processing parameters...')
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.processParameters(this)
		}
	}

	@NonCPS
	final void addStringParameter(String name, String defaultValue, String description) {
		def parameter = rawScript.string(name: PARAM_PREFIX + name, defaultValue: defaultValue, description: description)
		parameters.add(parameter)
	}

	@NonCPS
	final void addBooleanParameter(String name, boolean defaultValue, String description) {
		def parameter = rawScript.booleanParam(name: PARAM_PREFIX + name, defaultValue: defaultValue, description: description)
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

	/**
	 * Obtain first non-null value from params followed by env (params access will be prefixed with P_).
	 * If required and both are null throw an exception otherwise return null.
	 */
	@NonCPS
	private Object retrieveValueFromParamsOrEnv(String name, boolean required = true) {
		// P_ prefix needed otherwise params overwrite env
		def value = rawScript.params['P_' + name] ?: rawScript.env.getEnvironment()[name] ?: null
		if (value || !required) {
			return value
		}
		throw new RuntimeException("${name} is neither in env nor in params")
	}

	final Object retrieveOptionalValue(String name, Object defaultValue) {
		return retrieveValueFromParamsOrEnv(name, false) ?: defaultValue
	}

	final Object retrieveRequiredValue(String name) {
		return retrieveValueFromParamsOrEnv(name)
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

	private void updateJobProperties() {
		List jobProperties = []

		jobProperties.add(rawScript.parameters(parameters))

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
	}

}
