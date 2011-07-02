/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.stillingar.core;

import java.util.List;

/**
 * TODO
 * 
 * @author Andrew Taylor
 */
public final class ValueDefinitionGroup {

	private final String name;
	
	private final List<ValueDefinition<?>> values;
	
	private final GroupChangeListener changeListener;

	public ValueDefinitionGroup(String name, List<ValueDefinition<?>> values, GroupChangeListener changeListener) {
		this.name = name;
		this.values = values;
		this.changeListener = changeListener;
	}

	public String getName() {
		return name;
	}

	public List<ValueDefinition<?>> getValues() {
		return values;
	}

	public GroupChangeListener getChangeListener() {
		return changeListener;
	}
}
