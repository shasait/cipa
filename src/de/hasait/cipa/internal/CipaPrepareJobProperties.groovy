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

package de.hasait.cipa.internal

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.CipaPrepare
import de.hasait.cipa.activity.AbstractCipaBean
import de.hasait.cipa.jobprops.CipaParamValueProvider
import de.hasait.cipa.jobprops.JobParameterContribution
import de.hasait.cipa.jobprops.JobPropertiesContribution
import de.hasait.cipa.jobprops.PJobPropertiesManager
import de.hasait.cipa.log.PLogger

class CipaPrepareJobProperties extends AbstractCipaBean implements CipaPrepare {

	static final int PREPARE_CIPA_ORDER = 2000

	private final PLogger logger
	private final PJobPropertiesManager manager

	CipaPrepareJobProperties(rawScriptOrCipa) {
		super(rawScriptOrCipa)

		logger = new PLogger(rawScript, CipaPrepareJobProperties.class.simpleName)
		manager = new PJobPropertiesManager(script)
	}

	@Override
	@NonCPS
	int getPrepareCipaOrder() {
		return PREPARE_CIPA_ORDER
	}

	@Override
	void prepareCipa(Cipa cipa) {
		manager.addParamValueProviders(cipa.findBeansAsList(CipaParamValueProvider.class))

		List<JobParameterContribution> parameterContributions = cipa.findBeansAsList(JobParameterContribution.class)

		logger.info("Collecting parameters via ${JobParameterContribution.class.simpleName}s...")
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.contributeParameters(manager)
		}

		List<JobPropertiesContribution> propertiesContributions = cipa.findBeansAsList(JobPropertiesContribution.class)

		logger.info("Collecting job properties via ${JobPropertiesContribution.class.simpleName}s...")
		for (JobPropertiesContribution propertiesContribution in propertiesContributions) {
			propertiesContribution.contributeJobProperties(manager)
		}

		logger.info('Updating job properties...')
		manager.applyJobProperties()

		logger.info('Processing parameters...')
		for (JobParameterContribution parameterContribution in parameterContributions) {
			parameterContribution.processParameters(manager)
		}
	}

}
