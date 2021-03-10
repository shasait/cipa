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
import de.hasait.cipa.tool.CipaToolContribution

/**
 *
 */
class CipaToolNodeHandler extends AbstractCipaNodeHandler {

	private static final String TOOL_TYPE___JDK = 'hudson.model.JDK'
	private static final String TOOL_TYPE___MAVEN = 'hudson.tasks.Maven$MavenInstallation'

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

		List<CipaToolContribution> toolContributions = cipa.findBeansAsList(CipaToolContribution.class)
		if (!toolContributions.empty) {
			CipaToolContainerDelegate toolContainerDelegate = new CipaToolContainerDelegate(node, this)
			for (toolContribution in toolContributions) {
				toolContribution.contributeCipaTools(toolContainerDelegate)
			}
		}

		List<CipaTool> tools = cipa.findBeansAsList(CipaTool.class)
		for (tool in tools) {
			if (tool.node == null || tool.node.is(node)) {
				def toolHome = rawScript.tool(name: tool.name, type: tool.type)
				script.echo("[CIPA] Tool ${tool.name}: ${toolHome}")
				if (tool.dedicatedEnvVar) {
					envVars.add("${tool.dedicatedEnvVar}=${toolHome}")
				}
				if (tool.addToPathWithSuffix) {
					pathEntries.add("${toolHome}${tool.addToPathWithSuffix}")
				}
				if (tool.type == TOOL_TYPE___MAVEN) {
					String mvnRepo = script.determineMvnRepo()
					script.echo("[CIPA] mvnRepo: ${mvnRepo}")
					envVars.add("${Cipa.ENV_VAR___MVN_REPO}=${mvnRepo}")
					envVars.add("${Cipa.ENV_VAR___MVN_OPTIONS}=-Dmaven.multiModuleProjectDirectory=\"${toolHome}\" ${tool.options} ${rawScript.env[Cipa.ENV_VAR___MVN_OPTIONS] ?: ''}")
				}

				List<List<String>> configFileEnvVarsList = tool.buildConfigFileEnvVarsList()
				for (configFileEnvVar in configFileEnvVarsList) {
					configFiles.add(rawScript.configFile(fileId: configFileEnvVar[1], variable: configFileEnvVar[0]))
				}
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
	CipaTool configureJDK(String version, CipaNode node = null) {
		CipaTool tool
		if (node == null) {
			if (toolJdk == null) {
				toolJdk = new CipaTool()
				cipa.addBean(toolJdk)
			}
			tool = toolJdk
		} else {
			tool = new CipaTool(node)
			cipa.addBean(tool)
		}

		tool.name = version
		tool.type = TOOL_TYPE___JDK
		tool.addToPathWithSuffix = '/bin'
		tool.dedicatedEnvVar = Cipa.ENV_VAR___JDK_HOME
		return tool
	}

	@NonCPS
	CipaTool configureMaven(String version, String mvnSettingsFileId, String mvnToolchainsFileId, CipaNode node = null) {
		CipaTool tool
		if (node == null) {
			if (toolMvn == null) {
				toolMvn = new CipaTool(node)
				cipa.addBean(toolMvn)
			}
			tool = toolMvn
		} else {
			tool = new CipaTool(node)
			cipa.addBean(tool)
		}

		tool.name = version
		tool.type = TOOL_TYPE___MAVEN
		tool.addToPathWithSuffix = '/bin'
		tool.dedicatedEnvVar = Cipa.ENV_VAR___MVN_HOME
		if (mvnSettingsFileId) {
			tool.addConfigFileEnvVar(Cipa.ENV_VAR___MVN_SETTINGS, mvnSettingsFileId)
		}
		if (mvnToolchainsFileId) {
			tool.addConfigFileEnvVar(Cipa.ENV_VAR___MVN_TOOLCHAINS, mvnToolchainsFileId)
		}
		return tool
	}

	@NonCPS
	CipaTool configureTool(String name, String type, CipaNode node = null) {
		CipaTool tool = new CipaTool(node)
		tool.name = name
		tool.type = type
		return cipa.addBean(tool)
	}

}
