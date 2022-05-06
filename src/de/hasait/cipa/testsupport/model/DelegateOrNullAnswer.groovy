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

package de.hasait.cipa.testsupport.model

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class DelegateOrNullAnswer implements Answer {

	final Object delegate

	DelegateOrNullAnswer(Object delegate) {
		this.delegate = delegate
	}

	@Override
	Object answer(InvocationOnMock invocation) throws Throwable {
		Method method = invocation.method
		Method delegateMethod
		if (method.declaringClass.isAssignableFrom(delegate.getClass())) {
			delegateMethod = method
		} else {
			try {
				delegateMethod = delegate.getClass().getMethod(method.name, method.parameterTypes)
			} catch (NoSuchMethodException ignored) {
				return null
			}
		}
		try {
			return delegateMethod.invoke(delegate, invocation.arguments)
		} catch (InvocationTargetException e) {
			throw e.cause
		}
	}

}
