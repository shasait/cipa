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

package de.hasait.cipa

/**
 *
 */
interface CipaBeanContainer {

	public <T> T addBean(T bean)

	public <T> Set<T> findBeans(Class<T> type)

	public <T> List<T> findBeansAsList(Class<T> type)

	public <T> T findBean(Class<T> type, boolean optional)

	public <T> T findBean(Class<T> type)

	public <T> T findOrAddBean(Class<T> type, Supplier<T> constructor)

	public <T> T findOrAddBean(Class<T> type)

}
