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

package de.hasait.cipa.test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull

import de.hasait.cipa.PScript
import de.hasait.cipa.testsupport.RawScriptTestBase
import org.junit.Before
import org.junit.Test

class PScriptTest extends RawScriptTestBase {

	PScript script

	@Before
	void init() {
		initRawScript('SomeFolder/SomeJob')

		script = new PScript(rawScript)
	}

	@Test
	void test_readFile_noContent() {
		String content = script.readFile('foo.txt')
		assertNull(content)
	}

	@Test
	void test_readFile_withContent() {
		String expectedContent = 'bar'
		rawScript.readFileContents['foo\\.txt'] = expectedContent
		String content = script.readFile('foo.txt')
		assertEquals(expectedContent, content)
	}

	@Test
	void test_readLibraryResource_noContent() {
		String content = script.readLibraryResource('foo.txt')
		assertNull(content)
	}

	@Test
	void test_readLibraryResource_withContent() {
		String expectedContent = 'bar'
		rawScript.libraryResourceContents['foo\\.txt'] = expectedContent
		String content = script.readLibraryResource('foo.txt')
		assertEquals(expectedContent, content)
	}

	@Test
	void test_sh_noResult() {
		String content = script.sh('pwd')
		assertEquals('', content)
	}

	@Test
	void test_sh_withResult() {
		String expectedContent = '/bar/foo'
		rawScript.shResults['pwd'] = expectedContent
		String content = script.sh('pwd', true)
		assertEquals(expectedContent, content)
	}

}
