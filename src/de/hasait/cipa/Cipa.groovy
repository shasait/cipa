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

package de.hasait.cipa

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.activity.CipaActivityInfo
import de.hasait.cipa.activity.CipaAfterActivities
import de.hasait.cipa.activity.CipaFileResourceCleanup
import de.hasait.cipa.activity.StageAroundActivity
import de.hasait.cipa.activity.StashFilesActivity
import de.hasait.cipa.activity.TimeoutAroundActivity
import de.hasait.cipa.activity.UnstashFilesActivity
import de.hasait.cipa.activity.UpdateGraphAroundActivity
import de.hasait.cipa.artifactstore.CipaArtifactStore
import de.hasait.cipa.artifactstore.DefaultCipaArtifactStoreSupplier
import de.hasait.cipa.internal.CipaActivityBuilder
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.internal.CipaBeanRegistration
import de.hasait.cipa.internal.CipaPrepareEnv
import de.hasait.cipa.internal.CipaPrepareJobProperties
import de.hasait.cipa.internal.CipaPrepareNodeLabelPrefix
import de.hasait.cipa.internal.CipaRunContext
import de.hasait.cipa.resource.CipaCustomResource
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

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
	private final boolean waitForCbpAvailable
	private final CipaPrepareNodeLabelPrefix nodeLabelPrefixHolder

	private final Map<Object, CipaBeanRegistration> beanRegistrations = new LinkedHashMap<>()

	private final Set<CipaInit> alreadyInitialized = new HashSet<>()
	private final Set<CipaPrepare> alreadyPrepared = new HashSet<>()

	private boolean waitForCbpEnabled = true
	private String finishedCbpFormat = 'Activity-%s-Finished'

	private CipaTool toolJdk
	private CipaTool toolMvn

	private CipaRunContext runContext

	boolean debug = false

	Cipa(rawScript) {
		if (rawScript == null) {
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

		waitForCbpAvailable = StepDescriptor.byFunctionName('waitForCustomBuildProperties') != null

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
	public <T> T addBean(T bean, String name = null) {
		beanRegistrations.put(bean, new CipaBeanRegistration(bean, name))
		return bean
	}

	@NonCPS
	void addStandardBeans(Integer defaultTimeoutInMinutes = null, boolean enableCleanup = true, boolean enableGraph = true) {
		findOrAddBean(StageAroundActivity.class)
		findOrAddBean(TimeoutAroundActivity.class).withDefaultTimeoutInMinutes(defaultTimeoutInMinutes)
		if (enableGraph) {
			findOrAddBean(UpdateGraphAroundActivity.class)
		}
		if (enableCleanup) {
			findOrAddBean(CipaFileResourceCleanup.class)
		}
	}

	@Override
	@NonCPS
	public <T> Set<T> findBeans(Class<T> type) {
		Set<T> results = new LinkedHashSet<>()
		for (bean in beanRegistrations.keySet()) {
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
		for (bean in beanRegistrations.keySet()) {
			if (type.isInstance(bean)) {
				results.add((T) bean)
			}
		}
		return results
	}

	@Override
	@NonCPS
	public <T> T findBean(Class<T> type, boolean optional = false, String name = null) {
		T result = null
		for (beanRegistration in beanRegistrations.values()) {
			Object bean = beanRegistration.bean
			if (type.isInstance(bean)) {
				if (name == null || name.equals(beanRegistration.name)) {
					if (result == null) {
						result = (T) bean
					} else {
						throw new IllegalStateException("Multiple beans found: ${type}")
					}
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
	 * Creation is either done by specified supplier; if not specified then the following constructors are tried:<ul>
	 *     <li>cipa, name (if name != null)</li>
	 *     <li>rawScript, name (if name != null)</li>
	 *     <li>cipa</li>
	 *     <li>rawScript</li>
	 *     <li>name (if name != null)</li>
	 *     <li>no arg</li>
	 * </ul>
	 *
	 * @param type The type of the bean, never null.
	 * @param supplier Optional construction strategy.
	 * @return The bean, never null.
	 */
	@Override
	@NonCPS
	public <T> T findOrAddBean(Class<T> type, Supplier<T> supplier = null, String name = null) {
		T bean = findBean(type, true, name)
		if (bean != null) {
			return bean
		}
		T newBean
		if (supplier != null) {
			newBean = supplier.get()
		}
		if (newBean == null && name != null) {
			try {
				newBean = type.getConstructor(Cipa.class, String.class).newInstance(this, name)
			} catch (NoSuchMethodException ignored) {
				// ignore
			}
		}
		if (newBean == null && name != null) {
			try {
				newBean = type.newInstance(rawScript, name)
			} catch (GroovyRuntimeException e) {
				analyzeGroovyRuntimeException(e)
			}
		}
		if (newBean == null) {
			try {
				newBean = type.getConstructor(Cipa.class).newInstance(this)
			} catch (NoSuchMethodException ignored) {
				// ignore
			}
		}
		if (newBean == null) {
			try {
				newBean = type.newInstance(rawScript)
			} catch (GroovyRuntimeException e) {
				analyzeGroovyRuntimeException(e)
			}
		}
		if (newBean == null && name != null) {
			try {
				newBean = type.newInstance(name)
			} catch (GroovyRuntimeException e) {
				analyzeGroovyRuntimeException(e)
			}
		}
		if (newBean == null) {
			newBean = type.newInstance()
		}

		return beanRegistrations.containsKey(newBean) ? newBean : addBean(newBean, name)
	}

	@NonCPS
	private void analyzeGroovyRuntimeException(GroovyRuntimeException e) {
		if (!e.message.startsWith('Could not find matching constructor for:')) {
			throw e
		}
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
		if (toolJdk == null) {
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
		if (toolMvn == null) {
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

		// if no other CipaArtifactStore exists create the default one
		findOrAddBean(CipaArtifactStore.class, new DefaultCipaArtifactStoreSupplier(this))

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

	@NonCPS
	Map<CipaNode, List<CipaActivityInfo>> getActivityInfosByNode() {
		return Collections.unmodifiableMap(runContext.wrappersByNode)
	}

	@NonCPS
	List<CipaActivityInfo> getActivityInfos() {
		return Collections.unmodifiableList(runContext.wrappers)
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
						// will never run so mark here
						waitForCbpMarkFinished(wrapper)
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
					List<NodeCleanup> cleanupResources = findBeansAsList(NodeCleanup.class)
					for (cleanResource in cleanupResources) {
						cleanResource.cleanupNode(node)
					}
				}
			}
		}
	}

	/**
	 * Use the old way (waitUntil) to wait for dependencies.
	 */
	@NonCPS
	void disableWaitForCustomBuildProperties() {
		waitForCbpEnabled = false
	}

	/**
	 * @param format The format to build the custom build property key from the activity name, e.g. Activity-%s-Finished.
	 */
	@NonCPS
	void setFinishedCustomBuildPropertyFormat(String format) {
		finishedCbpFormat = format
	}

	private Closure parallelActivityRunBranch(CipaActivityWrapper wrapper) {
		return {
			if (waitForCbpUsed) {
				List<String> notDoneDependencyNames = wrapper.readyToRunActivity(false)
				List<String> cbpKeys = notDoneDependencyNames.collect { String.format(finishedCbpFormat, it) }
				if (!cbpKeys.empty) {
					rawScript.waitForCustomBuildProperties(keys: cbpKeys)
				}
			} else {
				int countWait = 0
				rawScript.waitUntil() {
					countWait++
					List<String> notDoneDependencyNames = wrapper.readyToRunActivity(true)
					if (countWait > 10 && !notDoneDependencyNames.empty) {
						rawScript.echo("Activity [${wrapper.activity.name}] still waits for at least one dependency: ${notDoneDependencyNames}")
						countWait = 0
					}
					return notDoneDependencyNames.empty
				}
			}
			wrapper.runActivity()
			waitForCbpMarkFinished(wrapper)
		}
	}

	private void waitForCbpMarkFinished(CipaActivityWrapper wrapper) {
		if (waitForCbpUsed) {
			String finishedCbpKey = String.format(finishedCbpFormat, wrapper.activity.name)
			// wrapper.finishedDate will be null in most error cases, which is ok
			rawScript.setCustomBuildProperty(key: finishedCbpKey, value: wrapper.finishedDate)
		}
	}

	@NonCPS
	private boolean isWaitForCbpUsed() {
		return waitForCbpAvailable && waitForCbpEnabled
	}

	@NonCPS
	String nodeLabel(CipaNode cipaNode) {
		return (cipaNode.applyPrefix ? nodeLabelPrefixHolder.nodeLabelPrefix : '') + cipaNode.label
	}

	void node(CipaNode cipaNode, Closure body) {
		rawScript.node(nodeLabel(cipaNode)) {
			CipaWorkspaceProvider workspaceProvider = findBean(CipaWorkspaceProvider.class, true)
			if (workspaceProvider == null) {
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
