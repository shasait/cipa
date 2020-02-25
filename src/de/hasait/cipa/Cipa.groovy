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

import de.hasait.cipa.activity.CipaFileResourceCleanup
import de.hasait.cipa.resource.CipaResourceCleanup

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.activity.CipaAfterActivities
import de.hasait.cipa.activity.StageAroundActivity
import de.hasait.cipa.activity.StashFilesActivity
import de.hasait.cipa.activity.TimeoutAroundActivity
import de.hasait.cipa.activity.UnstashFilesActivity
import de.hasait.cipa.activity.UpdateGraphAroundActivity
import de.hasait.cipa.internal.CipaActivityBuilder
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.internal.CipaPrepareEnv
import de.hasait.cipa.internal.CipaPrepareJobProperties
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

	private static final ConcurrentHashMap<Object, Cipa> instances = new ConcurrentHashMap<>()

	private final def rawScript
	private final PScript script
	private final CipaPrepareNodeLabelPrefix nodeLabelPrefixHolder

	private final Set<Object> beans = new LinkedHashSet<>()

	private CipaTool toolJdk
	private CipaTool toolMvn

	private final Set<CipaInit> alreadyInitialized = new HashSet<>()
	private final Set<CipaPrepare> alreadyPrepared = new HashSet<>()

	CipaRunContext runContext

	boolean debug = false

	Cipa(rawScript) {
		if (!rawScript) {
			throw new IllegalArgumentException('rawScript is null')
		}
		try {
			rawScript.currentBuild
		} catch (MissingPropertyException e) {
			throw new IllegalArgumentException('Invalid rawScript', e)
		}
		this.rawScript = rawScript

		Cipa existingCipa = instances.putIfAbsent(rawScript, this)
		if (existingCipa != null) {
			throw new IllegalArgumentException('Duplicate construction - use getOrCreate')
		}
		script = addBean(new PScript(rawScript))
		addBean(new CipaPrepareEnv())
		addBean(new CipaPrepareJobProperties())
		nodeLabelPrefixHolder = addBean(new CipaPrepareNodeLabelPrefix())
	}

	@NonCPS
	static Cipa getOrCreate(rawScript) {
		Cipa existingCipa = instances.get(rawScript)
		if (existingCipa != null) {
			return existingCipa
		}
		return new Cipa(rawScript)
	}

	@Override
	@NonCPS
	public <T> T addBean(T bean) {
		beans.add(bean)
		return bean
	}

	@NonCPS
	void addStandardBeans(Integer defaultTimeoutInMinutes = null) {
		findOrAddBean(StageAroundActivity.class)
		findOrAddBean(TimeoutAroundActivity.class).withDefaultTimeoutInMinutes(defaultTimeoutInMinutes)
		findOrAddBean(UpdateGraphAroundActivity.class)
		findOrAddBean(CipaFileResourceCleanup.class)
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
		if (result == null && !optional) {
			throw new IllegalStateException("No bean found: ${type}")
		}
		return result
	}

	/**
	 * Either return already existing bean or create a new one.
	 * Creation is either done by specified supplier; if not specified then constructor with cipa arg is tried, then rawscript, then no-arg.
	 *
	 * @param type The type of the bean, never null.
	 * @param supplier Optional construction strategy.
	 * @return The bean, never null.
	 */
	@Override
	@NonCPS
	public <T> T findOrAddBean(Class<T> type, Supplier<T> supplier = null) {
		T bean = findBean(type, true)
		if (bean != null) {
			return bean
		}
		T newBean
		if (supplier != null) {
			newBean = supplier.get()
		} else {
			try {
				newBean = type.getConstructor(Cipa.class).newInstance(this)
			} catch (NoSuchMethodException e1) {
				try {
					newBean = type.newInstance(rawScript)
				} catch (GroovyRuntimeException e2) {
					newBean = type.newInstance()
				}
			}
		}
		return beans.contains(newBean) ? newBean : addBean(newBean)
	}

	@NonCPS
	CipaNode newNode(String nodeLabel, boolean applyPrefix = true) {
		return addBean(new CipaNode(nodeLabel, applyPrefix))
	}

	@NonCPS
	CipaActivityBuilder newActivity(CipaNode node) {
		return new CipaActivityBuilder(this, node)
	}

	@NonCPS
	CipaResourceWithState<CipaStashResource> newStashActivity(String name, CipaResourceWithState<CipaFileResource> files, String subDir = '', boolean withStage = false) {
		return new StashFilesActivity(this, name, files, subDir, withStage).providedStash
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> newUnstashActivity(String name, CipaResourceWithState<CipaStashResource> stash, CipaNode node, String relDir = null, boolean withStage = false) {
		return new UnstashFilesActivity(this, name, stash, node, relDir, withStage).providedFiles
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
	public <R extends CipaResource> CipaResourceWithState<R> newResourceWithState(R resource, String state) {
		return addBean(new CipaResourceWithState<R>(addBean(resource), state))
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
		List<CipaInit> result = findBeansAsList(CipaInit.class)
		result.removeAll(alreadyInitialized)
		return result
	}

	private void initBeans() {
		int round = 0
		while (true) {
			List<CipaInit> beans = findBeansToInitialize()
			if (beans.empty) {
				break
			}
			round++
			if (round > 100) {
				throw new IllegalStateException("Init loop? ${beans}")
			}
			for (bean in beans) {
				rawScript.echo("[CIPA] Initializing: ${bean}")
				bean.initCipa(this)
				alreadyInitialized.add(bean)
			}
		}
	}

	@NonCPS
	private List<CipaPrepare> findBeansToPrepare() {
		List<CipaPrepare> result = findBeansAsList(CipaPrepare.class)
		result.removeAll(alreadyPrepared)
		result.sort { it.prepareCipaOrder }
		return result
	}

	private void prepareBeans() {
		int round = 0
		while (true) {
			initBeans()

			List<CipaPrepare> beans = findBeansToPrepare()
			if (beans.empty) {
				break
			}
			if (round > 0) {
				rawScript.echo("[CIPA] Warning - Preparing late beans - prepare order might be incorrect!")
			}
			round++
			if (round > 100) {
				throw new IllegalStateException("Prepare loop? ${beans}")
			}
			for (bean in beans) {
				rawScript.echo("[CIPA] Preparing: ${bean}")
				bean.prepareCipa(this)
				alreadyPrepared.add(bean)
			}
		}
	}

	@Override
	void run() {
		prepareBeans()

		rawScript.echo("[CIPA] Creating RunContext...")
		runContext = new CipaRunContext(this)

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

	/**
	 * Do not contribute param; just read values from environment.
	 */
	@NonCPS
	void disableNodeLabelPrefixParam() {
		nodeLabelPrefixHolder.disableParams()
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

					for (wrapper in nodeWrappers) {
						wrapper.cleanupNode()
					}

					if (runContext.allFinished) {
						List<CipaAfterActivities> afters = findBeansAsList(CipaAfterActivities.class)
						for (CipaAfterActivities after in afters) {
							after.afterCipaActivities()
						}
					}

					// Will be invoked after all activities for each node
					List<CipaResourceCleanup> cleanupResources = findBeansAsList(CipaResourceCleanup.class)
					cleanupResources.forEach({ cleanResource ->
						cleanResource.performCleanup(node)
					})
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
				String notDoneDependency = wrapper.readyToRunActivity()
				if (countWait > 10 && notDoneDependency) {
					rawScript.echo("Activity [${wrapper.activity.name}] still waits for dependency [${notDoneDependency}] (and may be more)")
					countWait = 0
				}
				return notDoneDependency == null
			}
			wrapper.runActivity()
		}
	}

	@NonCPS
	String nodeLabel(CipaNode cipaNode) {
		return (cipaNode.applyPrefix ? nodeLabelPrefixHolder.nodeLabelPrefix : '') + cipaNode.label
	}

	void node(CipaNode cipaNode, Closure body) {
		rawScript.node(nodeLabel(cipaNode)) {
			CipaWorkspaceProvider workspaceProvider = findBean(CipaWorkspaceProvider.class, true)
			if (!workspaceProvider) {
				body()
			} else {
				String wsPath = workspaceProvider.determineWorkspacePath()
				rawScript.ws(wsPath) {
					body()
				}
			}
		}
	}

	private void nodeWithEnv(CipaNode cipaNode, Closure body) {
		node(cipaNode) {
			nodeWithEnvLogic(cipaNode, body)
		}
	}

	private void nodeWithEnvLogic(CipaNode node, Closure body) {
		node.runtimeHostname = script.determineHostname()
		rawScript.echo('[CIPA] On host: ' + node.runtimeHostname)
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
