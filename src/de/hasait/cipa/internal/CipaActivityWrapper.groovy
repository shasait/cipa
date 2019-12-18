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
import de.hasait.cipa.PScript
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaActivityRunContext
import de.hasait.cipa.activity.CipaActivityWithCleanup
import de.hasait.cipa.activity.CipaAroundActivity
import de.hasait.cipa.activity.CipaLogFile
import de.hasait.cipa.activity.CipaTestResult
import de.hasait.cipa.activity.CipaTestResults
import hudson.model.Result
import hudson.model.Run
import hudson.tasks.junit.CaseResult
import hudson.tasks.junit.TestResultAction

import java.text.SimpleDateFormat
import java.util.regex.Pattern

class CipaActivityWrapper implements CipaActivityRunContext, Serializable {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat('yyyy-MM-dd\' \'HH:mm:ss\' \'Z')

	@NonCPS
	private static String format(Date date) {
		return date ? DATE_FORMAT.format(date) : ''
	}

	private final Cipa cipa
	private final PScript script
	final CipaActivity activity
	private final List<CipaAroundActivity> aroundActivities

	private final Map<CipaActivityWrapper, Boolean> dependsOn = new LinkedHashMap<>()

	private final Date creationDate
	private Throwable prepareThrowable
	private Date startedDate
	private Date finishedDate
	private Throwable runThrowable
	private List<CipaActivityWrapper> failedDependencies
	private Throwable cleanupThrowable

	private final List<CipaLogFile> logfiles = new ArrayList<>()

	private final CipaTestResults testResults = new CipaTestResults()

	CipaActivityWrapper(Cipa cipa, PScript script, CipaActivity activity, List<CipaAroundActivity> aroundActivities) {
		this.cipa = cipa
		this.script = script
		this.activity = activity
		this.aroundActivities = aroundActivities

		creationDate = new Date()
	}

	@NonCPS
	void addDependency(CipaActivityWrapper activity, boolean propagateFailure = true) {
		dependsOn.put(activity, propagateFailure)
	}

	@NonCPS
	Set<Map.Entry<CipaActivityWrapper, Boolean>> getDependencies() {
		return Collections.unmodifiableSet(dependsOn.entrySet())
	}

	@NonCPS
	Date getCreationDate() {
		return creationDate
	}

	@NonCPS
	Throwable getCleanupThrowable() {
		return cleanupThrowable
	}

	@NonCPS
	Throwable getPrepareThrowable() {
		return prepareThrowable
	}

	@NonCPS
	void setPrepareThrowable(Throwable prepareThrowable) {
		if (!prepareThrowable) {
			throw new IllegalArgumentException('!prepareThrowable')
		}
		this.prepareThrowable = prepareThrowable
	}

	@NonCPS
	List<CipaActivityWrapper> getFailedDependencies() {
		return failedDependencies ? Collections.unmodifiableList(failedDependencies) : null
	}

	@NonCPS
	Date getStartedDate() {
		return startedDate
	}

	@NonCPS
	Date getFinishedDate() {
		return finishedDate
	}

	@NonCPS
	Throwable getRunThrowable() {
		return runThrowable
	}

	@NonCPS
	boolean isFailed() {
		return prepareThrowable || failedDependencies || runThrowable
	}

	@NonCPS
	boolean isDone() {
		return failed || finishedDate
	}

	@NonCPS
	boolean isRunning() {
		return startedDate && !done
	}

	@NonCPS
	CipaTestResults getTestResults() {
		return testResults
	}

	@NonCPS
	String buildFailedMessage() {
		if (!failed) {
			return null
		}

		if (prepareThrowable) {
			return prepareThrowable.message
		}
		if (failedDependencies) {
			return buildFailedWrappersMessage('Dependencies', failedDependencies)
		}
		if (runThrowable) {
			return runThrowable.message
		}

		return 'Unknown (BUG?!)'
	}


	@Override
	@NonCPS
	void addPassedTest(String description) {
		testResults.add(new CipaTestResult(description))
	}

	@Override
	@NonCPS
	void addFailedTest(String description, int failingAge) {
		testResults.add(new CipaTestResult(description, failingAge))
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
	void archiveLogFile(String srcPath, String title = null) {
		script.archiveArtifacts(srcPath)
		logfiles.add(new CipaLogFile(srcPath, title))
	}

	@Override
	void archiveMvnLogFile(String tgtPath, String title = null) {
		script.sh("mv -vf ${PScript.MVN_LOG} \"${tgtPath}\"")
		archiveLogFile(tgtPath, title)
	}

	@NonCPS
	List<CipaLogFile> getLogfiles() {
		return Collections.unmodifiableList(logfiles)
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
		if (!testResults.empty) {
			sb << " | TestResults: ${testResults.countPassed}/${testResults.countTotal} (${testResults.countFailed} failed)"
		}
		return sb.toString()
	}

	void cleanupNode() {
		try {
			if (activity instanceof CipaActivityWithCleanup) {
				((CipaActivityWithCleanup) activity).cleanupNode()
			}
		} catch (Throwable throwable) {
			cleanupThrowable = throwable
			script.echoStacktrace('cleanupNode', throwable)
		}
	}

	void prepareNode() {
		try {
			activity.prepareNode()
		} catch (Throwable throwable) {
			prepareThrowable = throwable
			script.echoStacktrace('prepareNode', throwable)
		}
	}

	void runActivity() {
		String notDoneDependencyName = readyToRunActivity()
		if (notDoneDependencyName) {
			throw new IllegalStateException("At least one not done dependency exists: ${notDoneDependencyName}")
		}

		if (done) {
			throw new IllegalStateException('Already done')
		}

		failedDependencies = findFailedDependencyWrappers()
		if (failedDependencies) {
			for (CipaAroundActivity aroundActivity in aroundActivities) {
				try {
					aroundActivity.handleFailedDependencies(this)
				} catch (Throwable throwable) {
					script.echoStacktrace('handleFailedDependencies', throwable)
					throw throwable
				}
			}
			return
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			try {
				aroundActivity.beforeActivityStarted(this)
			} catch (Throwable throwable) {
				script.echoStacktrace('beforeActivityStarted', throwable)
				throw throwable
			}
		}

		try {
			startedDate = new Date()
			runAroundActivity(0)
		} catch (Throwable throwable) {
			runThrowable = throwable
			script.echoStacktrace('runActivity', throwable)
		} finally {
			finishedDate = new Date()
		}

		if (runThrowable) {
			script.currentRawBuild.result = Result.FAILURE
		} else if (!testResults.stable) {
			script.currentRawBuild.result = Result.UNSTABLE
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			try {
				aroundActivity.afterActivityFinished(this)
			} catch (Throwable throwable) {
				script.echoStacktrace('afterActivityFinished', throwable)
				throw throwable
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
	String readyToRunActivity() {
		for (dependencyWrapper in dependsOn.keySet()) {
			if (!dependencyWrapper.done) {
				return dependencyWrapper.activity.name
			}
		}

		return null
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
	static void throwOnAnyActivityFailure(String msgPrefix, Collection<CipaActivityWrapper> wrappers) {
		List<CipaActivityWrapper> failedWrappers = findFailedWrappers(wrappers)
		String msg = buildFailedWrappersMessage(msgPrefix, failedWrappers)
		if (msg) {
			throw new RuntimeException(msg)
		}
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
