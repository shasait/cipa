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
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertSame

import de.hasait.cipa.testsupport.CipaTestBase
import org.junit.Before
import org.junit.Test

/**
 *
 */
class CipaBeanTest extends CipaTestBase {

	@Before
	void init() {
		initCipa()
	}

	@Test
	void test_findBeans_none() {
		Set<A> beans = cipa.findBeans(A.class)

		assertNotNull(beans)
		assertEquals(0, beans.size())
	}

	@Test
	void test_findBean_none_optional() {
		A result = cipa.findBean(A.class, true)

		assertNull(result)
	}

	@Test(expected = IllegalStateException.class)
	void test_findBean_none_notOptional() {
		cipa.findBean(A.class, false)
	}

	@Test
	void test_findBeans_one() {
		A a1 = new A()
		cipa.addBean(a1)

		Set<A> beans = cipa.findBeans(A.class)

		assertNotNull(beans)
		assertEquals(1, beans.size())
		assertSame(a1, beans.iterator().next())
	}

	@Test
	void test_findBean_one() {
		A a1 = new A()
		cipa.addBean(a1)

		A result = cipa.findBean(A.class)
		assertSame(a1, result)
	}

	@Test
	void test_findBeans_two() {
		A a1 = new A()
		cipa.addBean(a1)
		A a2 = new A()
		cipa.addBean(a2)

		List<A> beans = cipa.findBeansAsList(A.class)

		assertNotNull(beans)
		assertEquals(2, beans.size())
		assertSame(a1, beans.get(0))
		assertSame(a2, beans.get(1))
	}

	@Test(expected = IllegalStateException.class)
	void test_findBean_two() {
		A a1 = new A()
		cipa.addBean(a1)
		A a2 = new A()
		cipa.addBean(a2)

		cipa.findBean(A.class)
	}

	@Test
	void test_findBean_two_namedOnlyOne() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		A a2 = new A()
		cipa.addBean(a2)

		A result = cipa.findBean(A.class, false, 'a1')

		assertSame(a1, result)
	}

	@Test(expected = IllegalStateException.class)
	void test_findBean_two_namedButFindNonNamed() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		A a2 = new A()
		cipa.addBean(a2)

		cipa.findBean(A.class)
	}

	@Test
	void test_findBean_two_namedAll() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		A a2 = new A()
		cipa.addBean(a2, 'a2')

		A result = cipa.findBean(A.class, false, 'a2')

		assertSame(a2, result)
	}

	@Test(expected = IllegalStateException.class)
	void test_findBean_two_namedNotFound() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		A a2 = new A()
		cipa.addBean(a2, 'a2')

		cipa.findBean(A.class, false, 'a3')
	}

	@Test
	void test_findBean_two_namedNotFound_optional() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		A a2 = new A()
		cipa.addBean(a2, 'a2')

		A result = cipa.findBean(A.class, true, 'a3')

		assertNull(result)
	}

	@Test
	void test_addBean_duplicate_ignored() {
		A a1 = new A()
		cipa.addBean(a1)
		cipa.addBean(a1)

		List beans = cipa.findBeansAsList(A.class)
		assertNotNull(beans)
		assertEquals(1, beans.size())
		assertEquals(a1, beans.get(0))
	}

	@Test(expected = IllegalStateException.class)
	void test_addBean_different_name_fails() {
		A a1 = new A()
		cipa.addBean(a1, 'a1')
		cipa.addBean(a1, 'a2')
	}

	private static class A {

	}

}
