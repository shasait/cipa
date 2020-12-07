/*
 * Copyright (C) 2020 by Sebastian Hasait (sebastian at hasait dot de)
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
import de.hasait.cipa.activity.CipaTestResult
import de.hasait.cipa.activity.CipaTestSummary

class CipaTestResultsManager implements Serializable {

	private final List<CipaTestResult> testResults = new ArrayList<>()

	private boolean stable = true
	private int maxFailingAge = 0
	private int countPassed = 0
	private int countFailed = 0

	@NonCPS
	void add(CipaTestResult result) {
		if (!result) {
			throw new IllegalArgumentException('!result')
		}

		testResults.add(result)

		if (result.failingAge) {
			stable = false
			countFailed++
			maxFailingAge = Math.max(maxFailingAge, result.failingAge)
		} else {
			countPassed++
		}
	}

	@NonCPS
	boolean isEmpty() {
		return countPassed == 0 && countFailed == 0
	}

	@NonCPS
	boolean isStable() {
		return stable
	}

	@NonCPS
	CipaTestSummary getTestSummary() {
		return new CipaTestSummary(stable, maxFailingAge, countPassed, countFailed)
	}

	@NonCPS
	List<CipaTestResult> getTestResults() {
		return Collections.unmodifiableList(testResults)
	}

	@NonCPS
	List<String> getNewFailingTests() {
		return testResults.findAll { it.failingAge && it.failingAge == 1 }.collect { it.description }
	}

	@NonCPS
	List<String> getStillFailingTests() {
		return testResults.findAll { it.failingAge && it.failingAge > 1 }.collect { it.description }
	}

}
