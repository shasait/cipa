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
import de.hasait.cipa.PJobPropertiesManager
import de.hasait.cipa.PScript

class CipaPrepareJobProperties implements CipaInit, CipaPrepare, JobParameterContainer, JobParameterValues, JobPropertiesContainer, Serializable {

	private PScript script
	private def rawScript
	private PJobPropertiesManager manager

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		rawScript = script.rawScript
		manager = new PJobPropertiesManager(rawScript)
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
		manager.applyJobProperties()

		script.echo('Processing parameters...')
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.processParameters(this)
		}
	}

	@Override
	@NonCPS
	final void addStringParameter(String name, String defaultValue, String description) {
		manager.addStringParameter(name, defaultValue, description)
	}

	@Override
	@NonCPS
	final void addBooleanParameter(String name, boolean defaultValue, String description) {
		manager.addBooleanParameter(name, defaultValue, description)
	}

	@Override
	@NonCPS
	final void addBooleanChoiceParameter(String name, Boolean defaultValue, String description) {
		manager.addBooleanChoiceParameter(name, defaultValue, description)
	}

	@Override
	@NonCPS
	final void addChoiceParameter(String name, List<String> choices, String description) {
		manager.addChoiceParameter(name, choices, description)
	}

	@Override
	@NonCPS
	final void addPipelineTrigger(def trigger) {
		manager.addPipelineTrigger(trigger)
	}

	@Override
	@NonCPS
	final void addCustomJobProperty(def customJobProperty) {
		manager.addCustomJobProperty(customJobProperty)
	}

	@Override
	@NonCPS
	final void setBuildDiscarder(def buildDiscarder) {
		manager.setBuildDiscarder(buildDiscarder)
	}

	@Override
	@NonCPS
	final void setRebuildSettingsAutoRebuild(boolean autoRebuild) {
		manager.setRebuildSettingsAutoRebuild(autoRebuild)
	}

	@Override
	@NonCPS
	final void setRebuildSettingsRebuildDisabled(boolean rebuildDisabled) {
		manager.setRebuildSettingsRebuildDisabled(rebuildDisabled)
	}

	@Override
	@NonCPS
	final Object retrieveOptionalValue(String name, Object defaultValue) {
		return manager.retrieveValueFromParametersOrEnvironment(name, false) ?: defaultValue
	}

	@Override
	@NonCPS
	final Object retrieveRequiredValue(String name) {
		return manager.retrieveValueFromParametersOrEnvironment(name)
	}

	@Override
	@NonCPS
	final String retrieveOptionalStringParameterValue(String name, String defaultValue) {
		return manager.retrieveOptionalStringParameterValue(name, defaultValue)
	}

	@Override
	@NonCPS
	final String retrieveRequiredStringParameterValue(String name) {
		return manager.retrieveRequiredStringParameterValue(name)
	}

	@Override
	@NonCPS
	final boolean retrieveOptionalBooleanChoiceParameterValue(String name, boolean defaultValue) {
		return manager.retrieveOptionalBooleanChoiceParameterValue(name, defaultValue)
	}

	@Override
	@NonCPS
	final boolean retrieveRequiredBooleanChoiceParameterValue(String name) {
		return manager.retrieveRequiredBooleanChoiceParameterValue(name)
	}

	@Override
	@NonCPS
	final boolean retrieveBooleanParameterValue(String name) {
		return manager.retrieveBooleanParameterValue(name)
	}

}
