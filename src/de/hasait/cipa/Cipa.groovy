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

package de.hasait.cipa

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.activity.CipaAfterActivities
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.internal.CipaPrepareEnv
import de.hasait.cipa.internal.CipaPrepareJobParameters
import de.hasait.cipa.internal.CipaPrepareNodeLabelPrefix
import de.hasait.cipa.resource.CipaCustomResource
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource

/**
 *
 */
class Cipa implements CipaBeanContainer, Runnable, Serializable {

	static final String ENV_VAR___JDK_HOME = 'JAVA_HOME'
	static final String ENV_VAR___MVN_HOME = 'M2_HOME'
	static final String ENV_VAR___MVN_REPO = 'MVN_REPO'
	static final String ENV_VAR___MVN_SETTINGS = 'MVN_SETTINGS'
	static final String ENV_VAR___MVN_TOOLCHAINS = 'MVN_TOOLCHAINS'
	static final String ENV_VAR___MVN_OPTIONS = 'MAVEN_OPTS'

	private final def rawScript
	private final PScript script
	private final CipaPrepareNodeLabelPrefix nodeLabelPrefixHolder

	private final Set<Object> beans = new LinkedHashSet<>()

	private CipaTool toolJdk
	private CipaTool toolMvn

	private final Set<CipaInit> alreadyInitialized = new HashSet<>()

	CipaRunContext runContext

	boolean debug = false

	Cipa(rawScript) {
		if (!rawScript) {
			throw new IllegalArgumentException('rawScript is null')
		}
		this.rawScript = rawScript
		script = addBean(new PScript(rawScript))
		addBean(new CipaPrepareEnv())
		addBean(new CipaPrepareJobParameters())
		nodeLabelPrefixHolder = addBean(new CipaPrepareNodeLabelPrefix())
	}

	@Override
	@NonCPS
	public <T> T addBean(T bean) {
		beans.add(bean)
		return bean
	}

	@Override
	@NonCPS
	public <T> Set<T> findBeans(Class<T> type) {
		Set<T> results = new LinkedHashSet<>()
		for (bean in beans) {
			if (type.isInstance(bean)) {
				results.add((T) bean)
			}
		}
		return results
	}

	@Override
	@NonCPS
	public <T> List<T> findBeansAsList(Class<T> type) {
		List<T> results = new ArrayList<>()
		for (bean in beans) {
			if (type.isInstance(bean)) {
				results.add((T) bean)
			}
		}
		return results
	}

	@Override
	@NonCPS
	public <T> T findBean(Class<T> type, boolean optional = false) {
		T result = null
		for (bean in beans) {
			if (type.isInstance(bean)) {
				if (!result) {
					result = (T) bean
				} else {
					throw new IllegalStateException("Multiple beans found: ${type}")
				}
			}
		}
		if (!result) {
			throw new IllegalStateException("No bean found: ${type}")
		}
		return result
	}

	@NonCPS
	CipaNode newNode(String nodeLabel) {
		return addBean(new CipaNode(nodeLabel))
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> newFileResourceWithState(CipaNode node, String relDir, String state) {
		CipaFileResource resource = addBean(new CipaFileResource(node, relDir))
		return addBean(new CipaResourceWithState<CipaFileResource>(resource, state))
	}

	@NonCPS
	CipaResourceWithState<CipaStashResource> newStashResourceWithState(String id, String srcRelDir, String state) {
		CipaStashResource resource = addBean(new CipaStashResource(id, srcRelDir))
		return addBean(new CipaResourceWithState<CipaStashResource>(resource, state))
	}

	@NonCPS
	CipaResourceWithState<CipaCustomResource> newCustomResourceWithState(CipaNode node = null, String type, String id, String state) {
		CipaCustomResource resource = addBean(new CipaCustomResource(node, type, id))
		return addBean(new CipaResourceWithState<CipaCustomResource>(resource, state))
	}

	@NonCPS
	public <R extends CipaResource> CipaResourceWithState<R> newResourceState(CipaResourceWithState<R> resourceWithState, String state) {
		return addBean(new CipaResourceWithState<R>(resourceWithState.resource, state))
	}

	CipaTool configureJDK(String version) {
		if (!toolJdk) {
			toolJdk = new CipaTool()
			addBean(toolJdk)
		}
		toolJdk.name = version
		toolJdk.type = 'hudson.model.JDK'
		toolJdk.addToPathWithSuffix = '/bin'
		toolJdk.dedicatedEnvVar = ENV_VAR___JDK_HOME
		return toolJdk
	}

	@NonCPS
	CipaTool configureMaven(String version, String mvnSettingsFileId = null, String mvnToolchainsFileId = null) {
		if (!toolMvn) {
			toolMvn = new CipaTool()
			addBean(toolMvn)
		}
		toolMvn.name = version
		toolMvn.type = 'hudson.tasks.Maven$MavenInstallation'
		toolMvn.addToPathWithSuffix = '/bin'
		toolMvn.dedicatedEnvVar = ENV_VAR___MVN_HOME
		if (mvnSettingsFileId) {
			toolMvn.addConfigFileEnvVar(ENV_VAR___MVN_SETTINGS, mvnSettingsFileId)
		}
		if (mvnToolchainsFileId) {
			toolMvn.addConfigFileEnvVar(ENV_VAR___MVN_TOOLCHAINS, mvnToolchainsFileId)
		}
		return toolMvn
	}

	@NonCPS
	CipaTool configureTool(String name, String type) {
		CipaTool tool = new CipaTool()
		tool.name = name
		tool.type = type
		addBean(tool)
		return tool
	}

	@NonCPS
	private List<CipaInit> findBeansToInitialize() {
		List<CipaInit> inits = findBeansAsList(CipaInit.class)
		inits.removeAll(alreadyInitialized)
		return inits
	}

	private void initBeans() {
		rawScript.echo("[CIPA] Initializing...")
		int initRound = 0
		while (true) {
			List<CipaInit> inits = findBeansToInitialize()
			if (inits.empty) {
				break
			}
			initRound++
			if (initRound > 100) {
				throw new IllegalStateException("Init loop? ${inits}")
			}
			for (init in inits) {
				rawScript.echo("[CIPA] Initializing: ${init}")
				init.initCipa(this)
				alreadyInitialized.add(init)
			}
		}
	}

	@NonCPS
	private List<CipaPrepare> findBeansToPrepare() {
		List<CipaPrepare> prepares = findBeansAsList(CipaPrepare.class)
		prepares.sort { it.prepareCipaOrder }
		return prepares
	}

	private void prepareBeans() {
		rawScript.echo("[CIPA] Preparing...")
		List<CipaPrepare> prepares = findBeansToPrepare()

		for (prepare in prepares) {
			rawScript.echo("[CIPA] Preparing: ${prepare}")
			prepare.prepareCipa(this)
		}

		initBeans()
	}

	@Override
	void run() {
		initBeans()
		prepareBeans()

		rawScript.echo("[CIPA] Creating RunContext...")
		runContext = new CipaRunContext(this)

		if (debug) {
			rawScript.echo("[CIPA-Debug] Printing dependencies in DOT format:")
			rawScript.echo(runContext.dotContent)
		}

		rawScript.echo("[CIPA] Executing activities...")
		def parallelNodeBranches = [:]
		for (int nodeI = 0; nodeI < runContext.nodes.size(); nodeI++) {
			CipaNode node = runContext.nodes.get(nodeI)
			List<CipaActivityWrapper> nodeWrappers = runContext.wrappersByNode.get(node)
			if (nodeWrappers.empty) {
				rawScript.echo("[CIPA] WARNING: ${node} has no activities!")
			}
			parallelNodeBranches["${nodeI}-${node.label}"] = parallelNodeWithActivitiesBranch(nodeI, node, nodeWrappers)
		}

		parallelNodeBranches.failFast = true
		rawScript.parallel(parallelNodeBranches)

		rawScript.echo(buildRunSummary())
		CipaActivityWrapper.throwOnAnyActivityFailure('Activities', runContext.wrappers)
	}

	@NonCPS
	private String buildRunSummary() {
		StringBuilder sb = new StringBuilder()
		sb.append('[CIPA] Done\nSummary of all activities:\n')
		for (wrapper in runContext.wrappers) {
			sb.append("- ${wrapper.activity.name}\n    ${wrapper.buildStateHistoryString()}\n")
		}
		return sb.toString()
	}

	private Closure parallelNodeWithActivitiesBranch(int nodeI, CipaNode node, List<CipaActivityWrapper> nodeWrappers) {
		return {
			def parallelActivitiesBranches = [:]
			for (int activityI = 0; activityI < nodeWrappers.size(); activityI++) {
				CipaActivityWrapper wrapper = nodeWrappers.get(activityI)
				parallelActivitiesBranches["${nodeI}-${activityI}-${wrapper.activity.name}"] = parallelActivityRunBranch(wrapper)
			}

			nodeWithEnv(node) {
				Throwable prepareThrowable = null
				for (wrapper in nodeWrappers) {
					wrapper.prepareNode()
					if (wrapper.prepareThrowable) {
						prepareThrowable = wrapper.prepareThrowable
						break
					}
				}
				if (prepareThrowable) {
					for (wrapper in nodeWrappers) {
						wrapper.prepareThrowable = prepareThrowable
					}
				} else {
					parallelActivitiesBranches.failFast = true
					rawScript.parallel(parallelActivitiesBranches)

					if (runContext.allFinished) {
						List<CipaAfterActivities> afters = findBeansAsList(CipaAfterActivities.class)
						for (CipaAfterActivities after in afters) {
							after.afterCipaActivities()
						}
					}
				}
			}
		}
	}

	private Closure parallelActivityRunBranch(CipaActivityWrapper wrapper) {
		return {
			int countWait = 0
			// TODO replace with sth silent
			rawScript.waitUntil() {
				countWait++
				String notFinishedDependency = wrapper.readyToRunActivity()
				if (countWait > 10 && notFinishedDependency) {
					rawScript.echo("Activity [${wrapper.activity.name}] still waits for dependency [${notFinishedDependency}] (and may be more)")
					countWait = 0
				}
				return notFinishedDependency == null
			}
			wrapper.runActivity()
			if (wrapper.failedThrowable) {
				StringWriter sw = new StringWriter()
				PrintWriter pw = new PrintWriter(sw)
				wrapper.failedThrowable.printStackTrace(pw)
				pw.flush()
				rawScript.echo(sw.toString())
			}
		}
	}

	private void nodeWithEnv(CipaNode node, Closure body) {
		rawScript.node(nodeLabelPrefixHolder.nodeLabelPrefix + node.label) {
			rawScript.echo('[CIPA] On host: ' + script.determineHostname())
			String workspace = rawScript.env.WORKSPACE
			rawScript.echo("[CIPA] workspace: ${workspace}")

			def envVars = []
			def pathEntries = []
			def configFiles = []

			List<CipaTool> tools = findBeansAsList(CipaTool.class)
			for (tool in tools) {
				def toolHome = rawScript.tool(name: tool.name, type: tool.type)
				rawScript.echo("[CIPA] Tool ${tool.name}: ${toolHome}")
				if (tool.dedicatedEnvVar) {
					envVars.add("${tool.dedicatedEnvVar}=${toolHome}")
				}
				if (tool.addToPathWithSuffix) {
					pathEntries.add("${toolHome}${tool.addToPathWithSuffix}")
				}
				if (tool.is(toolMvn)) {
					String mvnRepo = script.determineMvnRepo()
					rawScript.echo("[CIPA] mvnRepo: ${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_REPO}=${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_OPTIONS}=-Dmaven.multiModuleProjectDirectory=\"${toolHome}\" ${toolMvn.options} ${rawScript.env[ENV_VAR___MVN_OPTIONS] ?: ''}")
				}

				List<List<String>> configFileEnvVarsList = tool.buildConfigFileEnvVarsList()
				for (configFileEnvVar in configFileEnvVarsList) {
					configFiles.add(rawScript.configFile(fileId: configFileEnvVar[1], variable: configFileEnvVar[0]))
				}
			}

			envVars.add('PATH+=' + pathEntries.join(':'))

			rawScript.withEnv(envVars) {
				rawScript.configFileProvider(configFiles) {
					body()
				}
			}
		}
	}

}
