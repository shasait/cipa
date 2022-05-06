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

import hudson.model.Item
import hudson.model.ItemGroup
import hudson.security.Permission
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class TmItem<M extends Item> extends MockWrapper<M> implements TmItemAttributes {

	final String name
	final TmItemGroup tmParent

	String description

	protected TmItem(Class<M> mockClass, String name, TmItemGroup tmParent) {
		super(mockClass)
		this.name = name
		this.tmParent = tmParent
		if (tmParent != null) {
			tmParent.tmItems.add(this)
			// Workaround for Mockito expecting Jenkins returned from getParent vs. ItemGroup
			Mockito.doAnswer(new Answer() {
				@Override
				Object answer(InvocationOnMock invocation) throws Throwable {
					return tmParent.mock
				}
			}).when(mock).getParent()
		}
	}

	String getFullName() {
		return tmParent?.fullName ? tmParent.fullName + '/' + name : name
	}

	ItemGroup<? extends Item> getParent() {
		return tmParent?.mock
	}

	String getUrl() {
		return tmParent.url + shortUrl
	}

	String getShortUrl() {
		return 'job/' + name + '/'
	}

	String getAbsoluteUrl() {
		return tmParent.absoluteUrl + name + '/'
	}

	boolean hasPermission(Permission permission) {
		return true
	}

}
