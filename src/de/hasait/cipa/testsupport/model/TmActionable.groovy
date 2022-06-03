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

import java.util.concurrent.CopyOnWriteArrayList

import hudson.model.Action
import hudson.model.Actionable

class TmActionable<M extends Actionable> extends MockWrapper<M> {

	private final List<Action> actions = new CopyOnWriteArrayList<>()

	protected TmActionable(Class<M> mockClass) {
		super(mockClass)
	}

	List<Action> getActions() {
		return actions
	}

	List<? extends Action> getAllActions() {
		return actions.asImmutable()
	}

	public <T extends Action> T getAction(Class<T> type) {
		for (Action a : getActions()) {
			if (type.isInstance(a)) {
				return type.cast(a)
			}
		}
		return null
	}

	public <T extends Action> List<T> getActions(Class<T> type) {
		List<T> result = []
		for (Action a : getActions()) {
			if (type.isInstance(a)) {
				result.add(type.cast(a))
			}
		}
		return result.asImmutable()
	}

	void addAction(Action action) {
		actions.add(action)
	}

	boolean removeAction(Action action) {
		if (action == null) {
			return false
		}
		return actions.remove(action)
	}

	boolean removeActions(Class<? extends Action> clazz) {
		if (clazz == null) {
			throw new IllegalArgumentException('clazz == null')
		}
		List<Action> actionsToRemove = new ArrayList<Action>()
		for (Action action in actions) {
			if (clazz.isInstance(action)) {
				actionsToRemove.add(action)
			}
		}
		return actions.removeAll(actionsToRemove)
	}

	void replaceAction(Action action) {
		addOrReplaceAction(action)
	}

	boolean addOrReplaceAction(Action action) {
		if (action == null) {
			throw new IllegalArgumentException('action == null')
		}
		List<Action> actionsToRemove = new ArrayList<Action>(1)
		boolean found = false
		for (Action currentAction : actions) {
			if (!found && action == currentAction) {
				found = true
			} else if (currentAction.getClass() == action.getClass()) {
				actionsToRemove.add(currentAction)
			}
		}
		actions.removeAll(actionsToRemove)
		if (!found) {
			addAction(action)
		}
		return !found || !actionsToRemove.empty
	}

	boolean replaceActions(Class<? extends Action> clazz, Action action) {
		if (clazz == null) {
			throw new IllegalArgumentException('clazz == null')
		}
		if (action == null) {
			throw new IllegalArgumentException('action == null')
		}
		List<Action> actionsToRemove = new ArrayList<Action>()
		boolean found = false
		for (Action currentAction : actions) {
			if (!found) {
				if (action == currentAction) {
					found = true
				} else if (clazz.isInstance(currentAction)) {
					actionsToRemove.add(currentAction)
				}
			} else if (clazz.isInstance(currentAction) && action != currentAction) {
				actionsToRemove.add(currentAction)
			}
		}
		actions.removeAll(actionsToRemove)
		if (!found) {
			addAction(action)
		}
		return !found || !actionsToRemove.empty
	}

}
