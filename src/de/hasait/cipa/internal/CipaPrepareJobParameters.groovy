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
import de.hasait.cipa.Script

class CipaPrepareJobParameters implements CipaInit, CipaPrepare, JobParameterContainer, JobParameterValues, Serializable {

	private static final String PARAM_PREFIX = 'P_'

	private Script script
	private def rawScript

	private final List parameters = []

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(Script.class)
		rawScript = script.rawScript
	}

	@Override
	@NonCPS
	int getPrepareCipaOrder() {
		return 2000
	}

	@Override
	void prepareCipa(Cipa cipa) {
		Set<JobParameterContribution> parameterContributions = cipa.findBeans(JobParameterContribution.class)

		script.echo('contributeParameters')
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.contributeParameters(this)
		}

		script.echo('updateJobProperties')
		updateJobProperties()

		script.echo('processParameters')
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

	private void updateJobProperties() {
		rawScript.properties([
				rawScript.buildDiscarder(rawScript.logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '20')),
				rawScript.parameters(parameters),
				[$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
				rawScript.pipelineTriggers([rawScript.pollSCM('H/10 * * * *')])
		])
	}

}
