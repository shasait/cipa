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

import static de.hasait.cipa.PScript.ARCHIVE_ALLOW_EMPTY_DEFAULT
import static de.hasait.cipa.PScript.ARCHIVE_EXCLUDES_DEFAULT
import static de.hasait.cipa.PScript.ARCHIVE_INCLUDES_DEFAULT
import static de.hasait.cipa.PScript.ARCHIVE_USE_DEFAULT_EXCLUDES_DEFAULT
import static de.hasait.cipa.PScript.STASH_ALLOW_EMPTY_DEFAULT
import static de.hasait.cipa.PScript.STASH_EXCLUDES_DEFAULT
import static de.hasait.cipa.PScript.STASH_INCLUDES_DEFAULT
import static de.hasait.cipa.PScript.STASH_USE_DEFAULT_EXCLUDES_DEFAULT

import java.text.SimpleDateFormat
import java.util.regex.Pattern

import com.cloudbees.groovy.cps.NonCPS
import de.hasait.cipa.Cipa
import de.hasait.cipa.PScript
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaActivityInfo
import de.hasait.cipa.activity.CipaActivityPublished
import de.hasait.cipa.activity.CipaActivityPublishedLink
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.activity.CipaActivityWithCleanup
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.activity.CipaTestResult
import de.hasait.cipa.activity.CipaTestSummary
import de.hasait.cipa.artifactstore.CipaArtifactStore
import de.hasait.cipa.tool.MavenExecution
import hudson.model.Result
import hudson.model.Run
import hudson.tasks.junit.CaseResult
import hudson.tasks.junit.TestResultAction

class CipaActivityWrapper implements CipaActivityInfo, CipaActivityRunContext, Serializable {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat('yyyy-MM-dd\' \'HH:mm:ss\' \'Z')

	@NonCPS
	private static String format(Date date) {
		return date ? DATE_FORMAT.format(date) : ''
	}

	@NonCPS
	static void throwOnAnyActivityFailure(String msgPrefix, Collection<CipaActivityWrapper> wrappers) {
		List<CipaActivityWrapper> failedWrappers = findFailedWrappers(wrappers)
		String msg = buildFailedWrappersMessage(msgPrefix, failedWrappers)
		if (msg) {
			throw new RuntimeException(msg)
		}
	}

	private final Cipa cipa
	private final PScript script
	final CipaActivity activity
	private final List<CipaAroundActivity> aroundActivities

	private final Map<CipaActivityWrapper, Boolean> dependsOn = new LinkedHashMap<>()

	private final Date creationDate
	private Throwable prepareThrowable
	private Throwable externalPrepareThrowable
	private Date startedDate
	private Date finishedDate
	private Throwable runThrowable
	private List<CipaActivityWrapper> failedDependencies
	private Throwable aroundThrowable
	private Throwable cleanupThrowable

	private final List<CipaActivityPublished> published = new ArrayList<>()
	private final CipaTestResultsManager testResultsManager = new CipaTestResultsManager()

	private CipaArtifactStore cipaArtifactStore

	CipaActivityWrapper(Cipa cipa, PScript script, CipaActivity activity, List<CipaAroundActivity> aroundActivities) {
		this.cipa = cipa
		this.script = script
		this.activity = activity
		this.aroundActivities = aroundActivities

		creationDate = new Date()
	}

	@NonCPS
	void addDependency(CipaActivityWrapper activity, boolean propagateFailure = true) {
		Boolean currentValue = dependsOn.get(activity)
		if (currentValue == null || !currentValue) {
			dependsOn.put(activity, propagateFailure)
		}
	}

	@Override
	@NonCPS
	Set<Map.Entry<CipaActivityWrapper, Boolean>> getDependencies() {
		return Collections.unmodifiableSet(dependsOn.entrySet())
	}

	@Override
	@NonCPS
	Date getCreationDate() {
		return creationDate
	}

	@Override
	@NonCPS
	Date getStartedDate() {
		return startedDate
	}

	@Override
	@NonCPS
	Date getFinishedDate() {
		return finishedDate
	}

	@Override
	@NonCPS
	boolean isRunning() {
		return startedDate && !done
	}

	@Override
	@NonCPS
	boolean isDone() {
		return failed || finishedDate
	}

	@Override
	@NonCPS
	boolean isFailed() {
		return prepareThrowable || externalPrepareThrowable || failedDependencies || runThrowable || aroundThrowable
	}

	@Override
	@NonCPS
	String buildFailedMessage() {
		if (!failed) {
			return null
		}

		if (prepareThrowable) {
			return 'prepareNode failed - ' + prepareThrowable.message
		}
		if (externalPrepareThrowable) {
			return 'prepareNode of other activity failed - ' + externalPrepareThrowable.message
		}
		if (failedDependencies) {
			return buildFailedWrappersMessage('Dependencies', failedDependencies)
		}
		if (runThrowable) {
			return 'runActivity or runAroundActivity failed - ' + runThrowable.message
		}
		if (aroundThrowable) {
			return 'aroundActivity failed - ' + aroundThrowable.message
		}

		return 'Unknown failure'
	}

	@Override
	@NonCPS
	Throwable getPrepareThrowable() {
		return prepareThrowable
	}

	@Override
	@NonCPS
	Throwable getExternalPrepareThrowable() {
		return externalPrepareThrowable
	}

	@NonCPS
	void setExternalPrepareThrowable(Throwable externalPrepareThrowable) {
		if (externalPrepareThrowable == null) {
			throw new IllegalArgumentException('externalPrepareThrowable is null')
		}

		this.externalPrepareThrowable = externalPrepareThrowable
	}

	@Override
	@NonCPS
	List<CipaActivityWrapper> getFailedDependencies() {
		return failedDependencies ? Collections.unmodifiableList(failedDependencies) : null
	}

	@Override
	@NonCPS
	Throwable getRunThrowable() {
		return runThrowable
	}

	@Override
	@NonCPS
	Throwable getAroundThrowable() {
		return aroundThrowable
	}

	@Override
	@NonCPS
	Throwable getCleanupThrowable() {
		return cleanupThrowable
	}

	@NonCPS
	String buildStateHistoryString() {
		StringBuilder sb = new StringBuilder()
		sb << "Created: ${format(creationDate)}"
		if (failed) {
			sb << " | Failed: ${buildFailedMessage()}"
		}
		if (startedDate) {
			sb << " | Started: ${format(startedDate)}"
		}
		if (finishedDate) {
			sb << " | Finished: ${format(finishedDate)}"
		}
		CipaTestSummary testSummary = testSummary
		if (!testSummary.empty) {
			sb << " | TestResults: ${testSummary.countPassed}/${testSummary.countTotal} (${testSummary.countFailed} failed)"
		}
		return sb.toString()
	}

	void prepareNode() {
		try {
			activity.prepareNode()
		} catch (Throwable throwable) {
			prepareThrowable = throwable
			script.echo('prepareNode failed', throwable)
		}
	}

	void runActivity() {
		cipaArtifactStore = cipa.findBean(CipaArtifactStore.class)

		List<String> notDoneDependencyNames = readyToRunActivity(true)
		if (!notDoneDependencyNames.empty) {
			throw new IllegalStateException("At least one not done dependency exists: ${notDoneDependencyNames}")
		}

		if (done) {
			throw new IllegalStateException('Activity already done')
		}

		failedDependencies = findFailedDependencyWrappers()
		if (failedDependencies) {
			for (CipaAroundActivity aroundActivity in aroundActivities) {
				try {
					aroundActivity.handleFailedDependencies(this)
				} catch (Throwable throwable) {
					aroundThrowable = throwable
					script.echo('handleFailedDependencies failed', throwable)
				}
			}
			return
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			try {
				aroundActivity.beforeActivityStarted(this)
			} catch (Throwable throwable) {
				aroundThrowable = throwable
				script.echo('beforeActivityStarted failed', throwable)
			}
		}
		if (aroundThrowable) {
			return
		}

		try {
			startedDate = new Date()
			runAroundActivity(0)
		} catch (Throwable throwable) {
			runThrowable = throwable
			script.echo('runActivity or runAroundActivity failed', throwable)
		} finally {
			finishedDate = new Date()
		}

		if (runThrowable) {
			script.currentRawBuild.result = Result.FAILURE
		} else if (!testResultsManager.stable) {
			script.currentRawBuild.result = Result.UNSTABLE
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			try {
				aroundActivity.afterActivityFinished(this)
			} catch (Throwable throwable) {
				aroundThrowable = throwable
				script.echo('afterActivityFinished failed', throwable)
			}
		}
	}

	private void runAroundActivity(int i) {
		if (i < aroundActivities.size()) {
			aroundActivities.get(i).runAroundActivity(this, {
				runAroundActivity(i + 1)
			})
		} else {
			activity.runActivity(this)
		}
	}

	/**
	 * @return null if ready; otherwise the name of an not yet done dependency.
	 */
	@NonCPS
	List<String> readyToRunActivity(boolean onlyReturnFirst) {
		List<String> notDoneNames = []
		for (dependencyWrapper in dependsOn.keySet()) {
			if (!dependencyWrapper.done) {
				notDoneNames.add(dependencyWrapper.activity.name)
				if (onlyReturnFirst) {
					break
				}
			}
		}

		return notDoneNames
	}

	void cleanupNode() {
		try {
			if (activity instanceof CipaActivityWithCleanup) {
				((CipaActivityWithCleanup) activity).cleanupNode()
			}
		} catch (Throwable throwable) {
			cleanupThrowable = throwable
			script.echo('cleanupNode failed', throwable)
		}
	}

	@Override
	void archiveFiles(Set<String> includes = ARCHIVE_INCLUDES_DEFAULT, Set<String> excludes = ARCHIVE_EXCLUDES_DEFAULT, boolean useDefaultExcludes = ARCHIVE_USE_DEFAULT_EXCLUDES_DEFAULT, boolean allowEmpty = ARCHIVE_ALLOW_EMPTY_DEFAULT) {
		cipaArtifactStore.archiveFiles(includes, excludes, useDefaultExcludes, allowEmpty)
	}

	@Override
	void stash(String id, Set<String> includes = STASH_INCLUDES_DEFAULT, Set<String> excludes = STASH_EXCLUDES_DEFAULT, boolean useDefaultExcludes = STASH_USE_DEFAULT_EXCLUDES_DEFAULT, boolean allowEmpty = STASH_ALLOW_EMPTY_DEFAULT) {
		cipaArtifactStore.stash(id, includes, excludes, useDefaultExcludes, allowEmpty)
	}

	@Override
	void unstash(String id) {
		cipaArtifactStore.unstash(id)
	}

	@Override
	CipaActivityPublished archiveFile(String path) {
		return cipaArtifactStore.archiveFile(path)
	}

	@Override
	CipaActivityPublished archiveLogFile(String path) {
		return archiveFile(path)
	}

	@Override
	CipaActivityPublished archiveMvnLogFile(String tgtPath) {
		script.sh("mv -vf ${MavenExecution.MVN_LOG_FILE} \"${tgtPath}\"")
		return archiveLogFile(tgtPath)
	}

	@Override
	void publishFile(String path, String title = null) {
		addPublished(archiveFile(path), title)
	}

	@Override
	void publishLogFile(String path, String title = null) {
		addPublished(archiveLogFile(path), title)
	}

	@Override
	void publishMvnLogFile(String tgtPath, String title = null) {
		addPublished(archiveMvnLogFile(tgtPath), title)
	}

	@Override
	@NonCPS
	void publishLink(String url, String title = null) {
		addPublished(new CipaActivityPublishedLink(url), title)
	}

	@Override
	@NonCPS
	void addPublished(CipaActivityPublished newPublished, String title = null) {
		if (title) {
			newPublished.title = title
		}
		published.add(newPublished)
	}

	@Override
	@NonCPS
	List<CipaActivityPublished> getPublished() {
		return Collections.unmodifiableList(published)
	}

	@Override
	@NonCPS
	void addPassedTest(String description) {
		testResultsManager.add(new CipaTestResult(description))
	}

	@Override
	@NonCPS
	void addFailedTest(String description, int failingAge) {
		testResultsManager.add(new CipaTestResult(description, failingAge))
	}

	@Override
	void addJUnitTestResults(String includeRegex, String excludeRegex) {
		script.rawScript.junit('**/target/surefire-reports/*.xml')
		applyJUnitTestResults(includeRegex, excludeRegex)
	}

	@NonCPS
	void applyJUnitTestResults(String includeRegex, String excludeRegex) {
		Pattern includePattern = includeRegex && includeRegex.trim().length() > 0 ? Pattern.compile(includeRegex) : null
		Pattern excludePattern = excludeRegex && excludeRegex.trim().length() > 0 ? Pattern.compile(excludeRegex) : null

		Closure patternFilter = { CaseResult caseResult ->
			if (includePattern && !includePattern.matcher(caseResult.className).matches()) {
				return false
			}
			if (excludePattern && excludePattern.matcher(caseResult.className).matches()) {
				return false
			}
			return true
		}

		Run<?, ?> build = script.currentRawBuild
		synchronized (build) {
			TestResultAction testResultAction = build.getAction(TestResultAction.class)
			if (testResultAction) {
				if (testResultAction.passedTests) {
					testResultAction.passedTests.findAll(patternFilter).each { addPassedTest(it.fullName) }
				}
				if (testResultAction.failedTests) {
					testResultAction.failedTests.findAll(patternFilter).each { addFailedTest(it.fullName, it.age) }
				}
			}
		}
	}

	@Override
	@NonCPS
	CipaTestSummary getTestSummary() {
		return testResultsManager.testSummary
	}

	@Override
	@NonCPS
	List<CipaTestResult> getTestResults() {
		return testResultsManager.testResults
	}

	@Override
	@NonCPS
	List<CipaTestResult> getNewFailingTestResults() {
		return testResultsManager.newFailingTestResults
	}

	@Override
	@NonCPS
	List<CipaTestResult> getStillFailingTestResults() {
		return testResultsManager.stillFailingTestResults
	}

	@NonCPS
	private List<CipaActivityWrapper> findFailedDependencyWrappers() {
		Set<CipaActivityWrapper> wrappersToInheritFailuresFrom = new LinkedHashSet<>()
		for (wrapperWithInherit in dependsOn) {
			if (wrapperWithInherit.value.booleanValue()) {
				wrappersToInheritFailuresFrom.add(wrapperWithInherit.key)
			}
		}
		return findFailedWrappers(wrappersToInheritFailuresFrom)
	}

	@NonCPS
	private static List<CipaActivityWrapper> findFailedWrappers(Collection<CipaActivityWrapper> wrappers) {
		List<CipaActivityWrapper> failedWrappers = new ArrayList<>()
		for (wrapper in wrappers) {
			if (wrapper.failed) {
				failedWrappers.add(wrapper)
			}
		}
		return failedWrappers.empty ? null : failedWrappers
	}

	@NonCPS
	private static String buildFailedWrappersMessage(String msgPrefix, Collection<CipaActivityWrapper> failedWrappers) {
		if (!failedWrappers || failedWrappers.empty) {
			return null
		}

		StringBuilder sb = new StringBuilder(msgPrefix + ' failed: ')
		sb.append(failedWrappers.collect { "${it.activity.name} = ${it.buildFailedMessage()}" }.toString())
		return sb.toString()
	}

}
