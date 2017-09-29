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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS

class CipaTestResults implements Serializable {

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
	int getMaxFailingAge() {
		return maxFailingAge
	}

	@NonCPS
	int getCountPassed() {
		return countPassed
	}

	@NonCPS
	int getCountFailed() {
		return countFailed
	}

	@NonCPS
	int getCountTotal() {
		return testResults.size()
	}

	@NonCPS
	List<String> getNewFailingTests() {
		return testResults.findAll { it.failingAge && it.failingAge == 0 }.collect { it.description }
	}

}
