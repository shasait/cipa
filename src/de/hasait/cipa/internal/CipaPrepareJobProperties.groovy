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
import de.hasait.cipa.PScript
import de.hasait.cipa.jobprops.JobParameterContainer
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobParameterValues
import de.hasait.cipa.jobprops.JobPropertiesContainer
import de.hasait.cipa.jobprops.JobPropertiesContribution
import de.hasait.cipa.jobprops.PJobArgument
import de.hasait.cipa.jobprops.PJobPropertiesManager

class CipaPrepareJobProperties implements CipaInit, CipaPrepare, Serializable {

	private PScript script
	private PJobPropertiesManager manager

	@Override
	void initCipa(Cipa cipa) {
		script = cipa.findBean(PScript.class)
		manager = new PJobPropertiesManager(script)
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
			parameterContribution.contributeParameters(manager)
		}

		List<JobPropertiesContribution> propertiesContributions = cipa.findBeansAsList(JobPropertiesContribution.class)

		script.echo("Collecting job properties via ${JobPropertiesContribution.class.simpleName}s...")
		for (JobPropertiesContribution propertiesContribution in propertiesContributions) {
			propertiesContribution.contributeJobProperties(manager)
		}

		script.echo('Updating job properties...')
		manager.applyJobProperties()

		script.echo('Processing parameters...')
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.processParameters(manager)
		}
	}

}
