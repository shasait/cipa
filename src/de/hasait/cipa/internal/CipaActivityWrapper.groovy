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
import de.hasait.cipa.CipaNode
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
	private final CipaActivity activity
	private final List<CipaAroundActivity> aroundActivities

	private final Set<CipaActivityWrapper> dependsOn = new LinkedHashSet<>()

	private final Date creationDate
	private Throwable prepareThrowable
	private Date startedDate
	private Date finishedDate
	private Throwable failedThrowable

	CipaActivityWrapper(Cipa cipa, CipaActivity activity, List<CipaAroundActivity> aroundActivities) {
		this.cipa = cipa
		this.activity = activity
		this.aroundActivities = aroundActivities

		creationDate = new Date()
	}

	@NonCPS
	CipaNode getNode() {
		return activity.node
	}

	@NonCPS
	String getName() {
		return activity.name
	}

	@NonCPS
	void addDependency(CipaActivityWrapper activity) {
		dependsOn.add(activity)
	}

	@NonCPS
	Set<CipaActivityWrapper> getDependencies() {
		return dependsOn
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
		try {
			startedDate = new Date()
			throwOnAnyActivityFailure('Dependencies', dependsOn)
			runAroundActivity(0)

			finishedDate = new Date()
		} catch (Throwable throwable) {
			failedThrowable = throwable
			finishedDate = new Date()
		}
	}

	private void runAroundActivity(int i) {
		if (i < aroundActivities.size()) {
			aroundActivities.get(i).runAroundActivity(activity, { runAroundActivity(i + 1) })
		} else {
			activity.runActivity()
		}
	}

	/**
	 * @return null if ready; otherwise the name of an non-finished dependency.
	 */
	String readyToRunActivity() {
		for (dependency in dependsOn) {
			if (!dependency.finishedDate && !dependency.prepareThrowable) {
				return dependency.name
			}
		}

		return null
	}

	static void throwOnAnyActivityFailure(String msgPrefix, Set<CipaActivityWrapper> activities) {
		StringBuilder sb = null
		for (activity in activities) {
			if (activity.failedThrowable || activity.prepareThrowable) {
				if (!sb) {
					sb = new StringBuilder(msgPrefix + ' failed: [')
				} else {
					sb.append('; ')
				}
				sb.append(activity.name)
				sb.append(' = ')
				if (activity.failedThrowable) {
					sb.append(activity.failedThrowable.message)
				} else {
					sb.append(activity.prepareThrowable.message)
				}
			}
		}

		if (sb) {
			sb.append(']')
			throw new RuntimeException(sb.toString())
		}
	}

}
