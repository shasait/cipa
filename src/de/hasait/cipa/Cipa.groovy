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
import de.hasait.cipa.internal.CipaCleanupNodeHandler
import de.hasait.cipa.internal.CipaPrepareEnv
import de.hasait.cipa.internal.CipaPrepareJobProperties
import de.hasait.cipa.internal.CipaPrepareNodeLabelPrefix
import de.hasait.cipa.internal.CipaRunContext
import de.hasait.cipa.internal.CipaToolNodeHandler
import de.hasait.cipa.internal.CipaWorkspaceNodeHandler
import de.hasait.cipa.log.PLogLevel
import de.hasait.cipa.log.PLogger
import de.hasait.cipa.nodehandler.CipaNodeHandler
import de.hasait.cipa.resource.CipaCustomResource
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState
import de.hasait.cipa.resource.CipaStashResource
import de.hasait.cipa.runhandler.CipaRunHandler
import de.hasait.cipa.runhandler.TimeoutCipaRunHandler
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
	private final PLogger logger
	private final boolean waitForCbpAvailable
	private final CipaPrepareNodeLabelPrefix nodeLabelPrefixHolder
	private final CipaToolNodeHandler toolNodeHandler

	private final Map<Object, CipaBeanRegistration> beanRegistrations = new LinkedHashMap<>()

	private final Set<CipaInit> alreadyInitialized = new HashSet<>()
	private final Set<CipaPrepare> alreadyPrepared = new HashSet<>()

	private boolean waitForCbpEnabled = true
	private String finishedCbpFormat = 'Activity-%s-Finished'

	private CipaRunContext runContext

	private final List<CipaBeanRegistration> debugAddedBeans = []

	Cipa(rawScript) {
		if (rawScript == null) {
			throw new IllegalArgumentException('rawScript cannot be null')
		}
		try {
			rawScript.currentBuild
		} catch (MissingPropertyException e) {
			throw new IllegalArgumentException('Invalid rawScript - please pass "this" from Pipeline Script', e)
		}
		this.rawScript = rawScript

		logger = new PLogger(rawScript, Cipa.class.simpleName)

		Cipa existingCipa = instances.putIfAbsent(rawScript, this)
		if (existingCipa != null) {
			throw new IllegalArgumentException('Cipa instance for specified rawScript exists already - prefer "Cipa.getOrCreate" over "new Cipa"')
		}
		script = findOrAddBean(PScript.class)

		waitForCbpAvailable = StepDescriptor.byFunctionName('waitForCustomBuildProperties') != null

		findOrAddBean(CipaPrepareEnv.class)
		findOrAddBean(CipaPrepareJobProperties.class)
		nodeLabelPrefixHolder = findOrAddBean(CipaPrepareNodeLabelPrefix.class)
		findOrAddBean(CipaWorkspaceNodeHandler.class)
		toolNodeHandler = findOrAddBean(CipaToolNodeHandler.class)
		findOrAddBean(CipaCleanupNodeHandler.class)
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
		synchronized (beanRegistrations) {
			CipaBeanRegistration existingBeanRegistration = beanRegistrations.get(bean)
			if (existingBeanRegistration != null) {
				if (existingBeanRegistration.name != name) {
					throw new IllegalStateException("Bean already registered with different name: ${existingBeanRegistration.name} vs. ${name}")
				}
				return bean
			}

			CipaBeanRegistration newBeanRegistration = new CipaBeanRegistration(bean, name)
			if (debug) {
				debugAddedBeans.add(newBeanRegistration)
			}
			beanRegistrations.put(bean, newBeanRegistration)
			return bean
		}
	}

	@NonCPS
	void addStandardBeans(Integer defaultTimeoutInMinutes = null, boolean enableCleanup = true, boolean enableGraph = true) {
		findOrAddBean(StageAroundActivity.class)
		findOrAddBean(TimeoutCipaRunHandler.class).withTimeoutInMinutes(defaultTimeoutInMinutes)
		findOrAddBean(TimeoutAroundActivity.class)
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

		synchronized (beanRegistrations) {
			for (bean in beanRegistrations.keySet()) {
				if (type.isInstance(bean)) {
					results.add((T) bean)
				}
			}
		}

		return results
	}

	@Override
	@NonCPS
	public <T> List<T> findBeansAsList(Class<T> type) {
		List<T> results = new ArrayList<>()

		synchronized (beanRegistrations) {
			for (bean in beanRegistrations.keySet()) {
				if (type.isInstance(bean)) {
					results.add((T) bean)
				}
			}
		}

		return results
	}

	@Override
	@NonCPS
	public <T> Map<T, String> findBeansWithName(Class<T> type) {
		Map<T, String> results = new LinkedHashMap<>()

		synchronized (beanRegistrations) {
			for (beanRegistration in beanRegistrations.values()) {
				if (type.isInstance(beanRegistration.bean)) {
					results.put((T) beanRegistration.bean, beanRegistration.name)
				}
			}
		}

		return results
	}

	@Override
	@NonCPS
	public <T> T findBean(Class<T> type, boolean optional = false, String name = null) {
		T result = null

		synchronized (beanRegistrations) {
			for (beanRegistration in beanRegistrations.values()) {
				Object bean = beanRegistration.bean
				if (type.isInstance(bean)) {
					if (name == null || name.equals(beanRegistration.name)) {
						if (result == null) {
							result = (T) bean
						} else {
							throw new IllegalStateException('Multiple beans found: ' + type)
						}
					}
				}
			}
		}

		if (result == null && !optional) {
			throw new IllegalStateException('No bean found: ' + type)
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
		synchronized (beanRegistrations) {
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
					newBean = type.getConstructor(PScript.class, String.class).newInstance(script, name)
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
					newBean = type.getConstructor(PScript.class).newInstance(script)
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

			return addBean(newBean, name)
		}
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

	@NonCPS
	CipaTool configureJDK(String version) {
		return toolNodeHandler.configureJDK(version)
	}

	@NonCPS
	CipaTool configureMaven(String version, String mvnSettingsFileId = null, String mvnToolchainsFileId = null) {
		return toolNodeHandler.configureMaven(version, mvnSettingsFileId, mvnToolchainsFileId)
	}

	@NonCPS
	CipaTool configureTool(String name, String type) {
		return toolNodeHandler.configureTool(name, type)
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
				throw new IllegalStateException('Init loop? ' + beans)
			}
			for (bean in beans) {
				logger.info('Initializing: ' + bean)
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
			logAndClearDebugAddBean('Added beans after initBeans')

			List<CipaPrepare> beans = findBeansToPrepare()
			if (beans.empty) {
				break
			}
			if (round > 0) {
				logger.warn('Preparing late beans - prepare order might be incorrect!')
			}
			round++
			if (round > 100) {
				throw new IllegalStateException('Prepare loop? ' + beans)
			}
			for (bean in beans) {
				logger.info('Preparing: ' + bean)
				bean.prepareCipa(this)
				alreadyPrepared.add(bean)
				logAndClearDebugAddBean('Added beans after prepareCipa')
			}

		}
	}

	@Override
	void run() {
		logAndClearDebugAddBean('Added beans before init and prepare beans')
		prepareBeans()
		logAndClearDebugAddBean('Added beans after prepareBeans')

		// if no other CipaArtifactStore exists create the default one
		findOrAddBean(CipaArtifactStore.class, new DefaultCipaArtifactStoreSupplier(this))

		List<CipaRunHandler> cipaRunHandlers = determineRunHandlers()
		handleRun(cipaRunHandlers, 0) {
			logger.info('Creating RunContext...')
			runContext = new CipaRunContext(this)

			logger.info('Executing activities...')
			def parallelNodeBranches = [:]
			for (int nodeI = 0; nodeI < runContext.nodes.size(); nodeI++) {
				CipaNode node = runContext.nodes.get(nodeI)
				List<CipaActivityWrapper> nodeWrappers = runContext.wrappersByNode.get(node)
				if (nodeWrappers.empty) {
					logger.warn(node + ' has no activities!')
				}
				parallelNodeBranches["${nodeI}-${node.label}"] = parallelNodeWithActivitiesBranch(nodeI, node, nodeWrappers)
			}

			parallelNodeBranches.failFast = true
			rawScript.parallel(parallelNodeBranches)

			logger.info(buildRunSummary())
			CipaActivityWrapper.throwOnAnyActivityFailure('Activities', runContext.wrappers)
		}

		logAndClearDebugAddBean('Added beans')
	}

	@NonCPS
	private List<CipaRunHandler> determineRunHandlers() {
		List<CipaRunHandler> cipaRunHandlers = findBeansAsList(CipaRunHandler.class)
		cipaRunHandlers.sort({ it.handleRunOrder })
		return cipaRunHandlers
	}

	private void handleRun(List<CipaRunHandler> cipaRunHandlers, int i, Closure last) {
		if (i < cipaRunHandlers.size()) {
			cipaRunHandlers.get(i).handleRun() {
				handleRun(cipaRunHandlers, i + 1, last)
			}
		} else {
			last.call()
		}
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
		sb.append('Done\nSummary of all activities:\n')
		for (wrapper in runContext.wrappers) {
			sb.append("- ${wrapper.activity.name}\n    ${wrapper.buildStateHistoryString()}\n")
		}
		return sb.toString()
	}

	private Closure parallelNodeWithActivitiesBranch(int nodeI, CipaNode cipaNode, List<CipaActivityWrapper> nodeWrappers) {
		return {
			def parallelActivitiesBranches = [:]
			for (int activityI = 0; activityI < nodeWrappers.size(); activityI++) {
				CipaActivityWrapper wrapper = nodeWrappers.get(activityI)
				parallelActivitiesBranches["${nodeI}-${activityI}-${wrapper.activity.name}"] = parallelActivityRunBranch(wrapper)
			}

			node(cipaNode) {
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
						logger.info("Activity [${wrapper.activity.name}] still waits for at least one dependency: ${notDoneDependencyNames}")
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
			cipaNode.runtimeHostname = script.determineHostname()
			logger.info('On host: ' + cipaNode.runtimeHostname)

			List<CipaNodeHandler> cipaNodeHandlers = determineNodeHandlers()
			handleNode(cipaNode, cipaNodeHandlers, 0, body)
		}
	}

	@NonCPS
	private List<CipaNodeHandler> determineNodeHandlers() {
		List<CipaNodeHandler> cipaNodeHandlers = findBeansAsList(CipaNodeHandler.class)
		cipaNodeHandlers.sort({ it.handleNodeOrder })
		return cipaNodeHandlers
	}

	private void handleNode(CipaNode cipaNode, List<CipaNodeHandler> cipaNodeHandlers, int i, Closure last) {
		if (i < cipaNodeHandlers.size()) {
			cipaNodeHandlers.get(i).handleNode(cipaNode, {
				handleNode(cipaNode, cipaNodeHandlers, i + 1, last)
			})
		} else {
			last.call()
		}
	}

	@NonCPS
	void setDebug(boolean debug) {
		logger.setLogLevel(debug ? PLogLevel.DEBUG : PLogLevel.INFO)
	}

	@NonCPS
	boolean isDebug() {
		return logger.logLevelDebugOrHigher
	}

	@NonCPS
	private void logAndClearDebugAddBean(String state, String separator = '\n- ') {
		if (!debugAddedBeans.empty) {
			logger.debug(state + separator + debugAddedBeans.join(separator))
			debugAddedBeans.clear()
		}
	}

}
