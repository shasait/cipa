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

	private final CipaNode _node
	private final String _description
	private final Closure _body

	private final List<CipaActivity> _dependsOn = new ArrayList<>()

	private final Date _creationDate
	private Date _runningDate
	private Date _finishedDate
	private Throwable _failedThrowable

	CipaActivity(CipaNode pNode, String pDescription, Closure pBody) {
		if (!pNode) {
			throw new IllegalArgumentException('pNode')
		}
		if (!pDescription) {
			throw new IllegalArgumentException('pDescription')
		}
		_node = pNode
		_description = pDescription
		_body = pBody

		_creationDate = new Date()
	}

	CipaNode getNode() {
		return _node
	}

	String getDescription() {
		return _description
	}

	void addDependency(CipaActivity pActivity) {
		_dependsOn.add(pActivity)
	}

	Date getCreationDate() {
		return _creationDate
	}

	Date getRunningDate() {
		return _runningDate
	}

	Date getFinishedDate() {
		return _finishedDate
	}

	Throwable getFailedThrowable() {
		return _failedThrowable
	}

	String buildStateHistoryString() {
		StringBuilder sb = new StringBuilder()
		sb.append('Created: ')
		sb.append(format(_creationDate))
		if (_runningDate) {
			sb.append(' | Running: ')
			sb.append(format(_runningDate))
			if (_finishedDate) {
				sb.append(' | Finished: ')
				sb.append(format(_finishedDate))
				if (_failedThrowable) {
					sb.append(' - Failed: ')
					sb.append(_failedThrowable.getMessage())
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
			_runningDate = new Date()
			_body()
			_finishedDate = new Date()
		} catch (Throwable throwable) {
			_failedThrowable = throwable
			_finishedDate = new Date()
		}
	}

	boolean allDependenciesSucceeded() {
		for (dependency in _dependsOn) {
			if (!dependency.finishedDate) {
				return false
			}

			Throwable throwable = dependency.failedThrowable
			if (throwable) {
				throw new RuntimeException("Dependency failed: ${dependency.name}", throwable)
			}
		}

		return true
	}

}
