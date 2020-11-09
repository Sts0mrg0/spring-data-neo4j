/*
 * Copyright 2011-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.integration.shared;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * @author Michael J. Simons
 * @soundtrack Die Toten Hosen - Zurück zum Glück
 */
@Node
public class ParentNode {
	@Id @GeneratedValue
	private Long id;

	private String someAttribute;

	public Long getId() {
		return id;
	}

	public String getSomeAttribute() {
		return someAttribute;
	}

	public void setSomeAttribute(String someAttribute) {
		this.someAttribute = someAttribute;
	}
}