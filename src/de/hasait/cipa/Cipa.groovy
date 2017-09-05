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
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.internal.CipaActivityWrapper
import de.hasait.cipa.internal.CipaPrepareEnv
import de.hasait.cipa.internal.CipaPrepareJobParameters
import de.hasait.cipa.internal.CipaPrepareNodeLabelPrefix
import de.hasait.cipa.resource.CipaCustomResource
import de.hasait.cipa.resource.CipaFileResource
import de.hasait.cipa.resource.CipaResource
import de.hasait.cipa.resource.CipaResourceWithState

/**
 *
 */
class Cipa implements CipaBeanContainer, Runnable, Serializable {

	private static final String ENV_VAR___JDK_HOME = 'JAVA_HOME'
	private static final String ENV_VAR___MVN_HOME = 'M2_HOME'
	private static final String ENV_VAR___MVN_REPO = 'MVN_REPO'
	private static final String ENV_VAR___MVN_SETTINGS = 'MVN_SETTINGS'
	private static final String ENV_VAR___MVN_TOOLCHAINS = 'MVN_TOOLCHAINS'
	private static final String ENV_VAR___MVN_OPTIONS = 'MAVEN_OPTS'

	private final def rawScript
	private final Script script
	private final CipaPrepareNodeLabelPrefix nodeLabelPrefixHolder

	private final Set<Object> beans = new LinkedHashSet<>()

	private CipaTool toolJdk
	private CipaTool toolMvn

	private final Set<CipaTool> tools = new LinkedHashSet<>()

	private final Set<CipaInit> alreadyInitialized = new HashSet<>()

	Cipa(rawScript) {
		if (!rawScript) {
			throw new IllegalArgumentException('rawScript is null')
		}
		this.rawScript = rawScript
		this.script = new Script(rawScript)
		addBean(script)
		addBean(new CipaPrepareEnv())
		addBean(new CipaPrepareJobParameters())
		nodeLabelPrefixHolder = new CipaPrepareNodeLabelPrefix()
		addBean(nodeLabelPrefixHolder)
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
		Set<T> result = new LinkedHashSet<>()
		for (bean in beans) {
			if (type.isInstance(bean)) {
				result.add((T) bean)
			}
		}
		return result
	}

	@Override
	@NonCPS
	public <T> T findBean(Class<T> type, boolean optional = false) {
		Set<T> results = findBeans(type)
		Iterator<T> resultsI = results.iterator()
		if (!resultsI.hasNext()) {
			if (optional) {
				return null
			}
			throw new IllegalStateException("No bean found: ${type}")
		}
		T result = resultsI.next()
		if (resultsI.hasNext()) {
			throw new IllegalStateException("Multiple beans found: ${type}")
		}
		return result
	}

	@NonCPS
	CipaNode newNode(String nodeLabel) {
		CipaNode node = new CipaNode(nodeLabel)
		return addBean(node)
	}

	@NonCPS
	CipaResourceWithState<CipaFileResource> newFileResourceWithState(CipaNode node, String relDir, String state) {
		CipaFileResource resource = new CipaFileResource(node, relDir)
		addBean(resource)
		CipaResourceWithState<CipaFileResource> resourceWithState = new CipaResourceWithState<CipaFileResource>(resource, state)
		return addBean(resourceWithState)
	}

	@NonCPS
	CipaResourceWithState<CipaCustomResource> newCustomResourceWithState(CipaNode node = null, String type, String id, String state) {
		CipaCustomResource resource = new CipaCustomResource(node, type, id)
		addBean(resource)
		CipaResourceWithState<CipaCustomResource> resourceWithState = new CipaResourceWithState<CipaCustomResource>(resource, state)
		return addBean(resourceWithState)
	}

	@NonCPS
	public <R extends CipaResource> CipaResourceWithState<R> newResourceState(CipaResourceWithState<R> resourceWithState, String state) {
		CipaResourceWithState<R> newResourceWithState = new CipaResourceWithState<R>(resourceWithState.resource, state)
		return addBean(newResourceWithState)
	}

	CipaTool configureJDK(String version) {
		if (!toolJdk) {
			toolJdk = new CipaTool()
			tools.add(toolJdk)
		}
		toolJdk.name = version
		toolJdk.type = 'hudson.model.JDK'
		toolJdk.addToPathWithSuffix = '/bin'
		toolJdk.dedicatedEnvVar = ENV_VAR___JDK_HOME
		return toolJdk
	}

	CipaTool configureMaven(String version, String mvnSettingsFileId = null, String mvnToolchainsFileId = null) {
		if (!toolMvn) {
			toolMvn = new CipaTool()
			tools.add(toolMvn)
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

	CipaTool configureTool(String name, String type) {
		CipaTool tool = new CipaTool()
		tool.name = name
		tool.type = type
		tools.add(tool)
		return tool
	}

	@NonCPS
	private Set<CipaInit> findBeansToInitialize() {
		Set<CipaInit> inits = findBeans(CipaInit.class)
		inits.removeAll(alreadyInitialized)
		return inits
	}

	private void initBeans() {
		rawScript.echo("[CIPA] Initializing...")
		int initRound = 0
		while (true) {
			Set<CipaInit> inits = findBeansToInitialize()
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
		List<CipaPrepare> prepares = new ArrayList<>(findBeans(CipaPrepare.class))
		prepares.sort({ it.prepareCipaOrder })
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

	@NonCPS
	private List<CipaAroundActivity> findCipaAroundActivities() {
		List<CipaAroundActivity> aroundActivities = new ArrayList<>(findBeans(CipaAroundActivity.class))
		aroundActivities.sort({ it.runAroundActivityOrder })
		return aroundActivities
	}

	private void analyzeActivities(Set<CipaNode> nodes, Set<CipaActivityWrapper> wrappers, Map<CipaNode, List<CipaActivityWrapper>> activitiesByNode) {
		Set<CipaResourceWithState<?>> resources = findBeans(CipaResourceWithState.class)

		rawScript.echo("[CIPA] Analyzing activities...")
		for (node in nodes) {
			activitiesByNode.put(node, new ArrayList<>())
		}
		Map<CipaResourceWithState<?>, List<CipaActivityWrapper>> activitiesRequires = new HashMap<>()
		Map<CipaResourceWithState<?>, List<CipaActivityWrapper>> activitiesProvides = new HashMap<>()
		Set<CipaActivity> activities = findBeans(CipaActivity.class)
		List<CipaAroundActivity> aroundActivities = findCipaAroundActivities()
		for (activity in activities) {
			CipaNode node = activity.node
			if (!activitiesByNode.containsKey(node)) {
				throw new IllegalStateException("Node [${node}] unknown - either create with cipa.newNode or register with addBean!")
			}
			CipaActivityWrapper wrapper = new CipaActivityWrapper(this, activity, aroundActivities)
			wrappers.add(wrapper)
			activitiesByNode.get(node).add(wrapper)
			for (requires in activity.runRequires) {
				if (!resources.contains(requires)) {
					throw new IllegalStateException("Resource [${requires}] unknown - either create with cipa.new*Resource or register with addBean!")
				}
				if (!activitiesRequires.containsKey(requires)) {
					activitiesRequires.put(requires, new ArrayList<>())
				}
				activitiesRequires.get(requires).add(wrapper)
			}
			for (provides in activity.runProvides) {
				if (!resources.contains(provides)) {
					throw new IllegalStateException("Resource [${provides}] unknown - either create with cipa.new*Resource or register with addBean!")
				}
				if (!activitiesProvides.containsKey(provides)) {
					activitiesProvides.put(provides, new ArrayList<>())
				}
				activitiesProvides.get(provides).add(wrapper)
			}
		}
		for (requires in activitiesRequires) {
			if (!activitiesProvides.containsKey(requires.key)) {
				throw new IllegalArgumentException("Required resource [${requires.key}] not provided!")
			}
			List<CipaActivityWrapper> providesWrappers = activitiesProvides.get(requires.key)
			for (requiresWrapper in requires.value) {
				for (providesWrapper in providesWrappers) {
					requiresWrapper.addDependency(providesWrapper)
				}
			}
		}
	}

	@Override
	void run() {
		initBeans()
		prepareBeans()

		Set<CipaNode> nodes = findBeans(CipaNode.class)
		Set<CipaActivityWrapper> wrappers = new LinkedHashSet<>()
		Map<CipaNode, List<CipaActivityWrapper>> activitiesByNode = new HashMap<>()
		analyzeActivities(nodes, wrappers, activitiesByNode)

		rawScript.echo("[CIPA] Executing activities...")
		List<CipaNode> nodeList = new ArrayList<>(nodes)
		def parallelNodeBranches = [:]
		for (int i = 0; i < nodeList.size(); i++) {
			CipaNode node = nodeList.get(i)
			List<CipaActivityWrapper> nodeActivities = activitiesByNode.get(node)
			if (nodeActivities.empty) {
				rawScript.echo("[CIPA] WARNING: Node [${node}] has no activities!")
			}
			parallelNodeBranches["${i}-${node.label}"] = parallelNodeWithActivitiesBranch(node, nodeActivities)
		}

		parallelNodeBranches.failFast = true
		rawScript.parallel(parallelNodeBranches)

		rawScript.echo('[CIPA] Done - Summary of all activities:')

		for (wrapper in wrappers) {
			rawScript.echo("[CIPA] Activity: ${wrapper.name}")
			rawScript.echo("[CIPA]     ${wrapper.buildStateHistoryString()}")
		}

		CipaActivityWrapper.throwOnAnyActivityFailure('Activities', wrappers)
	}

	private Closure parallelNodeWithActivitiesBranch(CipaNode node, List<CipaActivityWrapper> nodeActivities) {
		return {
			def parallelActivitiesBranches = [:]
			for (int i = 0; i < nodeActivities.size(); i++) {
				CipaActivityWrapper activity = nodeActivities.get(i)
				parallelActivitiesBranches["${i}-${activity.name}"] = parallelActivityRunBranch(activity)
			}

			nodeWithEnv(node) {
				Throwable prepareThrowable = null
				for (activity in nodeActivities) {
					activity.prepareNode()
					if (activity.prepareThrowable) {
						prepareThrowable = activity.prepareThrowable
						break
					}
				}
				if (prepareThrowable) {
					for (activity in nodeActivities) {
						activity.prepareThrowable = prepareThrowable
					}
				} else {
					parallelActivitiesBranches.failFast = true
					rawScript.parallel(parallelActivitiesBranches)
				}
			}
		}
	}

	private Closure parallelActivityRunBranch(CipaActivityWrapper activity) {
		return {
			rawScript.waitUntil() {
				activity.readyToRunActivity()
			}
			activity.runActivity()
			if (activity.failedThrowable) {
				StringWriter sw = new StringWriter()
				PrintWriter pw = new PrintWriter(sw)
				activity.failedThrowable.printStackTrace(pw)
				pw.flush()
				rawScript.echo(sw.toString())
			}
		}
	}

	private void nodeWithEnv(CipaNode node, Closure body) {
		rawScript.node(nodeLabelPrefixHolder.nodeLabelPrefix + node.label) {
			rawScript.echo('[CIPA] On host: ' + determineHostname())
			def workspace = rawScript.env.WORKSPACE
			rawScript.echo("[CIPA] workspace: ${workspace}")

			def envVars = []
			def pathEntries = []
			def configFiles = []

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
					def mvnRepo = determineMvnRepo()
					rawScript.echo("[CIPA] mvnRepo: ${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_REPO}=${mvnRepo}")
					envVars.add("${ENV_VAR___MVN_OPTIONS}=-Dmaven.multiModuleProjectDirectory=\"${toolHome}\" ${toolMvn.options} ${rawScript.env[ENV_VAR___MVN_OPTIONS] ?: ''}")
				}
				for (configFileEnvVar in tool.configFileEnvVars) {
					configFiles.add(rawScript.configFile(fileId: configFileEnvVar.value, variable: configFileEnvVar.key))
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

	String determineHostname() {
		String hostnameRaw = rawScript.sh(returnStdout: true, script: 'hostname')
		return hostnameRaw.trim()
	}

	String determineMvnRepo() {
		String workspace = rawScript.env.WORKSPACE
		return workspace + '/.repo'
	}

	String mvn(
			List<String> goals,
			List<String> profiles = [],
			List<String> arguments = [],
			List<String> options = [],
			boolean returnStdout = false) {
		def allArguments = ['-B', '-V', '-e']
		if (rawScript.env[ENV_VAR___MVN_SETTINGS]) {
			allArguments.add('-s "${' + ENV_VAR___MVN_SETTINGS + '}"')
		}
		if (rawScript.env[ENV_VAR___MVN_TOOLCHAINS]) {
			allArguments.add('--global-toolchains "${' + ENV_VAR___MVN_TOOLCHAINS + '}"')
		}
		allArguments.add('-Dmaven.repo.local="${' + ENV_VAR___MVN_REPO + '}"')
		if (!profiles.isEmpty()) {
			allArguments.add('-P' + profiles.join(','))
		}
		allArguments.addAll(goals)
		allArguments.addAll(arguments)

		def allArgumentsString = allArguments.isEmpty() ? '' : allArguments.join(' ')

		def optionsString = options.join(' ')

		rawScript.withEnv(["${ENV_VAR___MVN_OPTIONS}=${optionsString} ${rawScript.env[ENV_VAR___MVN_OPTIONS] ?: ''}"]) {
			rawScript.sh(script: 'printenv | sort')
			return rawScript.sh(script: "mvn ${allArgumentsString}", returnStdout: returnStdout)
		}
	}

}
