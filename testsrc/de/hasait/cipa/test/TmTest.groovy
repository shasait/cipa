/*
 * Copyright (C) 2026 by Sebastian Hasait (sebastian at hasait dot de)
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

package de.hasait.cipa.test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

import de.hasait.cipa.testsupport.RawScriptTestBase
import org.junit.Before
import org.junit.Test

/**
 *
 */
class TmTest extends RawScriptTestBase {

	@Before
	void init() {
		initRawScript('Project/FooBar/Job123')
		currentTmJob.tmParent.tmParent.getOrCreateTmJob('Job234')
		currentTmJob.tmParent.getOrCreateTmJob('Job345')
	}

	@Test
	void test_basic_facts() {
		assertEquals(1, currentRun.number)
		assertEquals('Job123', currentJob.name)
		assertEquals('Project/FooBar/Job123', currentJob.fullName)
		assertEquals('Project/FooBar', currentTmJob.tmParent.fullName)
		assertEquals('Project', currentTmJob.tmParent.tmParent.fullName)
	}

	@Test
	void test_hierarchy_1() {
		def allJobs = currentTmJob.tmParent.tmParent.allJobs
		assertEquals(3, allJobs.size())
		def allJobFullNames = allJobs.collect { it.fullName }
		assertTrue(allJobFullNames.contains('Project/FooBar/Job123'))
		assertTrue(allJobFullNames.contains('Project/Job234'))
		assertTrue(allJobFullNames.contains('Project/FooBar/Job345'))
	}

	@Test
	void test_hierarchy_2() {
		def allJobs = currentTmJob.tmParent.allJobs
		assertEquals(2, allJobs.size())
		def allJobFullNames = allJobs.collect { it.fullName }
		assertTrue(allJobFullNames.contains('Project/FooBar/Job123'))
		assertTrue(allJobFullNames.contains('Project/FooBar/Job345'))
	}

}
