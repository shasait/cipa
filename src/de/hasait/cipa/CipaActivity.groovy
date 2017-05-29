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

import java.text.SimpleDateFormat

class CipaActivity implements Serializable {

	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat('yyyy-MM-dd\' \'HH:mm:ss\' \'Z')

	private static String format(Date date) {
		return date ? DATE_FORMAT.format(date) : ''
	}

	private final CipaNode node
	private final String description
	private final Closure body

	private final List<CipaActivity> dependsOn = new ArrayList<>()

	private final Date creationDate
	private Date runningDate
	private Date finishedDate
	private Throwable failedThrowable

	CipaActivity(CipaNode node, String description, Closure body) {
		if (!node) {
			throw new IllegalArgumentException('node')
		}
		if (!description) {
			throw new IllegalArgumentException('description')
		}
		this.node = node
		this.description = description
		this.body = body

		creationDate = new Date()
	}

	CipaNode getNode() {
		return node
	}

	String getDescription() {
		return description
	}

	void addDependency(CipaActivity activity) {
		dependsOn.add(activity)
	}

	Date getCreationDate() {
		return creationDate
	}

	Date getRunningDate() {
		return runningDate
	}

	Date getFinishedDate() {
		return finishedDate
	}

	Throwable getFailedThrowable() {
		return failedThrowable
	}

	String buildStateHistoryString() {
		StringBuilder sb = new StringBuilder()
		sb.append('Created: ')
		sb.append(format(creationDate))
		if (runningDate) {
			sb.append(' | Running: ')
			sb.append(format(runningDate))
			if (finishedDate) {
				sb.append(' | Finished: ')
				sb.append(format(finishedDate))
				if (failedThrowable) {
					sb.append(' - Failed: ')
					sb.append(failedThrowable.getMessage())
				}
			}
		}
		return sb.toString()
	}

	void runActivity() {
		if (!allDependenciesSucceeded()) {
			throw new IllegalStateException("!allDependenciesSucceeded")
		}
		try {
			runningDate = new Date()
			body()
			finishedDate = new Date()
		} catch (Throwable throwable) {
			failedThrowable = throwable
			finishedDate = new Date()
		}
	}

	boolean allDependenciesSucceeded() {
		for (dependency in dependsOn) {
			if (!dependency.finishedDate) {
				return false
			}

			Throwable throwable = dependency.failedThrowable
			if (throwable) {
				throw new RuntimeException("Dependency failed: ${dependency.description}", throwable)
			}
		}

		return true
	}

}
