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

package de.hasait.cipa.activity

import com.cloudbees.groovy.cps.NonCPS

class CipaTestSummary implements Serializable {

	private final boolean stable
	private final int maxFailingAge
	private final int countPassed
	private final int countFailed
	private final int countTotal

	CipaTestSummary(boolean stable, int maxFailingAge, int countPassed, int countFailed) {
		this.stable = stable
		this.maxFailingAge = maxFailingAge
		this.countPassed = countPassed
		this.countFailed = countFailed
		this.countTotal = countPassed + countFailed
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
		return countTotal
	}

}
