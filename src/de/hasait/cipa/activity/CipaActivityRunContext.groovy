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

interface CipaActivityRunContext {

	void archiveFiles(Set<String> includes, Set<String> excludes, boolean useDefaultExcludes, boolean allowEmpty)

	void archiveFiles(Set<String> includes, Set<String> excludes, boolean useDefaultExcludes)

	void archiveFiles(Set<String> includes, Set<String> excludes)

	void archiveFiles(Set<String> includes)

	void archiveFiles()

	void stash(String id, Set<String> includes, Set<String> excludes, boolean useDefaultExcludes, boolean allowEmpty)

	void stash(String id, Set<String> includes, Set<String> excludes, boolean useDefaultExcludes)

	void stash(String id, Set<String> includes, Set<String> excludes)

	void stash(String id, Set<String> includes)

	void stash(String id)

	void unstash(String id)

	CipaActivityPublished archiveFile(String path)

	CipaActivityPublished archiveLogFile(String path)

	CipaActivityPublished archiveMvnLogFile(String tgtPath)

	void publishFile(String path, String title)

	void publishFile(String path)

	void publishLogFile(String path, String title)

	void publishLogFile(String path)

	void publishMvnLogFile(String tgtPath, String title)

	void publishMvnLogFile(String tgtPath)

	void publishLink(String url, String title)

	void publishLink(String url)

	void addPublished(CipaActivityPublished newPublished, String title)

	void addPublished(CipaActivityPublished newPublished)

	void addPassedTest(String description)

	void addFailedTest(String description, int failingAge)

	void addJUnitTestResults(String includeRegex, String excludeRegex)

}
