/*
 * Copyright (C) 2021 by Sebastian Hasait (sebastian at hasait dot de)
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
import org.apache.commons.lang.StringUtils

class CipaActivityPublishedLink extends AbstractCipaActivityPublished {

	private final String url

	CipaActivityPublishedLink(String url) {
		super()

		if (!url) {
			throw new IllegalArgumentException('url is null or empty')
		}

		this.url = url
		this.title = StringUtils.substringAfterLast(StringUtils.removeEnd(url, '/'), '/')
	}

	@NonCPS
	String getUrl() {
		return url
	}

}
