/*
 * Copyright (C) 2021 by Sebastian Hasait (sebastian at hasait dot de)
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
import de.hasait.cipa.CipaNode
import de.hasait.cipa.CipaTool
import de.hasait.cipa.nodehandler.AbstractCipaNodeHandler

/**
 *
 */
class CipaToolNodeHandler extends AbstractCipaNodeHandler {

	private CipaTool toolJdk
	private CipaTool toolMvn

	CipaToolNodeHandler(Object rawScriptOrCipa) {
		super(rawScriptOrCipa)
	}

	@Override
	void handleNode(CipaNode node, Closure<?> next) {
		def envVars = []
		def pathEntries = []
		def configFiles = []

		List<CipaTool> tools = cipa.findBeansAsList(CipaTool.class)
		for (tool in tools) {
			def toolHome = rawScript.tool(name: tool.name, type: tool.type)
			script.echo("[CIPA] Tool ${tool.name}: ${toolHome}")
			if (tool.dedicatedEnvVar) {
				envVars.add("${tool.dedicatedEnvVar}=${toolHome}")
			}
			if (tool.addToPathWithSuffix) {
				pathEntries.add("${toolHome}${tool.addToPathWithSuffix}")
			}
			if (tool.is(toolMvn)) {
				String mvnRepo = script.determineMvnRepo()
				script.echo("[CIPA] mvnRepo: ${mvnRepo}")
				envVars.add("${Cipa.ENV_VAR___MVN_REPO}=${mvnRepo}")
				envVars.add("${Cipa.ENV_VAR___MVN_OPTIONS}=-Dmaven.multiModuleProjectDirectory=\"${toolHome}\" ${toolMvn.options} ${rawScript.env[Cipa.ENV_VAR___MVN_OPTIONS] ?: ''}")
			}

			List<List<String>> configFileEnvVarsList = tool.buildConfigFileEnvVarsList()
			for (configFileEnvVar in configFileEnvVarsList) {
				configFiles.add(rawScript.configFile(fileId: configFileEnvVar[1], variable: configFileEnvVar[0]))
			}
		}

		envVars.add('PATH+=' + pathEntries.join(':'))

		rawScript.withEnv(envVars) {
			rawScript.configFileProvider(configFiles) {
				next.call()
			}
		}
	}

	@Override
	@NonCPS
	int getHandleNodeOrder() {
		return -20000000
	}

	@NonCPS
	CipaTool configureJDK(String version) {
		if (toolJdk == null) {
			toolJdk = new CipaTool()
			cipa.addBean(toolJdk)
		}
		toolJdk.name = version
		toolJdk.type = 'hudson.model.JDK'
		toolJdk.addToPathWithSuffix = '/bin'
		toolJdk.dedicatedEnvVar = Cipa.ENV_VAR___JDK_HOME
		return toolJdk
	}

	@NonCPS
	CipaTool configureMaven(String version, String mvnSettingsFileId, String mvnToolchainsFileId) {
		if (toolMvn == null) {
			toolMvn = new CipaTool()
			cipa.addBean(toolMvn)
		}
		toolMvn.name = version
		toolMvn.type = 'hudson.tasks.Maven$MavenInstallation'
		toolMvn.addToPathWithSuffix = '/bin'
		toolMvn.dedicatedEnvVar = Cipa.ENV_VAR___MVN_HOME
		if (mvnSettingsFileId) {
			toolMvn.addConfigFileEnvVar(Cipa.ENV_VAR___MVN_SETTINGS, mvnSettingsFileId)
		}
		if (mvnToolchainsFileId) {
			toolMvn.addConfigFileEnvVar(Cipa.ENV_VAR___MVN_TOOLCHAINS, mvnToolchainsFileId)
		}
		return toolMvn
	}

	@NonCPS
	CipaTool configureTool(String name, String type) {
		CipaTool tool = new CipaTool()
		tool.name = name
		tool.type = type
		cipa.addBean(tool)
		return tool
	}

}
