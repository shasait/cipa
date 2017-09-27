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
import de.hasait.cipa.activity.CipaActivity
import de.hasait.cipa.activity.CipaAroundActivity

import java.text.SimpleDateFormat

class CipaActivityWrapper implements Serializable {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat('yyyy-MM-dd\' \'HH:mm:ss\' \'Z')

	@NonCPS
	private static String format(Date date) {
		return date ? DATE_FORMAT.format(date) : ''
	}

	private final Cipa cipa
	final CipaActivity activity
	private final List<CipaAroundActivity> aroundActivities

	private final Map<CipaActivityWrapper, Boolean> dependsOn = new LinkedHashMap<>()

	private final Date creationDate
	private Throwable prepareThrowable
	private Date startedDate
	private Date finishedDate
	private Throwable failedThrowable
	private boolean dependencyFailure

	CipaActivityWrapper(Cipa cipa, CipaActivity activity, List<CipaAroundActivity> aroundActivities) {
		this.cipa = cipa
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
		return dependsOn.entrySet()
	}

	@NonCPS
	Date getCreationDate() {
		return creationDate
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
	Date getStartedDate() {
		return startedDate
	}

	@NonCPS
	Date getFinishedDate() {
		return finishedDate
	}

	@NonCPS
	Throwable getFailedThrowable() {
		return failedThrowable
	}

	@NonCPS
	boolean isDependencyFailure() {
		return dependencyFailure
	}

	@NonCPS
	String buildStateHistoryString() {
		StringBuilder sb = new StringBuilder()
		sb.append('Created: ')
		sb.append(format(creationDate))
		if (startedDate) {
			sb.append(' | Started: ')
			sb.append(format(startedDate))
			if (finishedDate) {
				sb.append(' | Finished: ')
				sb.append(format(finishedDate))
				if (failedThrowable) {
					sb.append(' - Failed: ')
					sb.append(failedThrowable.message)
				}
			}
		}
		return sb.toString()
	}

	void prepareNode() {
		try {
			activity.prepareNode()
		} catch (Throwable throwable) {
			prepareThrowable = throwable
		}
	}

	void runActivity() {
		String notFinishedDependency = readyToRunActivity()
		if (notFinishedDependency) {
			throw new IllegalStateException("!readyToRunActivity: ${notFinishedDependency}")
		}
		if (prepareThrowable) {
			throw new IllegalStateException('prepareThrowable')
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			aroundActivity.beforeActivityStarted(this)
		}

		try {
			startedDate = new Date()

			List<CipaActivityWrapper> failedDependencies = collectFailedActivitiesMap(dependsOn)
			if (!failedDependencies.empty) {
				try {
					handleDependencyFailures(0, failedDependencies)
				} catch (Throwable dependencyThrowable) {
					dependencyFailure = true
					throw dependencyThrowable
				}
			}

			runAroundActivity(0)

			finishedDate = new Date()
		} catch (Throwable throwable) {
			failedThrowable = throwable
			finishedDate = new Date()
		}

		for (CipaAroundActivity aroundActivity in aroundActivities) {
			aroundActivity.afterActivityFinished(this)
		}
	}

	private void handleDependencyFailures(int i, List<CipaActivityWrapper> failedDependencies) {
		if (i < aroundActivities.size()) {
			aroundActivities.get(i).handleDependencyFailures(this, failedDependencies, {
				handleDependencyFailures(i + 1, failedDependencies)
			})
		} else {
			throwOnAnyActivityFailure('Dependencies', failedDependencies)
		}
	}

	private void runAroundActivity(int i) {
		if (i < aroundActivities.size()) {
			aroundActivities.get(i).runAroundActivity(this, {
				runAroundActivity(i + 1)
			})
		} else {
			activity.runActivity()
		}
	}

	/**
	 * @return null if ready; otherwise the name of an non-finished dependency.
	 */
	@NonCPS
	String readyToRunActivity() {
		for (dependency in dependsOn.keySet()) {
			if (!dependency.finishedDate && !dependency.prepareThrowable) {
				return dependency.activity.name
			}
		}

		return null
	}

	@NonCPS
	static List<CipaActivityWrapper> collectFailedActivitiesMap(Map<CipaActivityWrapper, Boolean> wrappers) {
		Set<CipaActivityWrapper> inheritFailures = new LinkedHashSet<>()
		for (wrapper in wrappers) {
			if (wrapper.value.booleanValue()) {
				inheritFailures.add(wrapper.key)
			}
		}
		return collectFailedActivities(inheritFailures)
	}

	@NonCPS
	static List<CipaActivityWrapper> collectFailedActivities(Collection<CipaActivityWrapper> wrappers) {
		List<CipaActivityWrapper> failed = new ArrayList<>()
		for (wrapper in wrappers) {
			if (wrapper.failedThrowable || wrapper.prepareThrowable) {
				failed.add(wrapper)
			}
		}
		return failed
	}

	@NonCPS
	static String buildExceptionMessage(String msgPrefix, Collection<CipaActivityWrapper> failed) {
		if (!failed || failed.empty) {
			return null
		}

		StringBuilder sb = new StringBuilder(msgPrefix + ' failed: [')
		sb.append(
				failed.collect {
					return "${it.activity.name} = ${it.prepareThrowable ? it.prepareThrowable.message : it.failedThrowable.message}"
				}.join('; ')
		)
		sb.append(']')
		return sb.toString()
	}

	@NonCPS
	static void throwOnAnyActivityFailure(String msgPrefix, Collection<CipaActivityWrapper> wrappers) {
		List<CipaActivityWrapper> failed = collectFailedActivities(wrappers)
		String msg = buildExceptionMessage(msgPrefix, failed)
		if (msg) {
			throw new RuntimeException(msg)
		}
	}

}
