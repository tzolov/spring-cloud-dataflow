/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.cloud.dataflow.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author Christian Tzolov
 */
public class TaskDefinitionToDslConverterTests {


	@Test
	public void unquotedArgumentTests() {
		reverseDslTest("timestamp --format=yyyy-MM-dd");
	}

	@Test
	public void singleQuotedArgumentTests() {
		reverseDslTest("timestamp --format='yyyy-MM-dd HH:mm:ss'");
	}

	@Test
	public void doubleQuotedArgumentTests() {
		TaskDefinition taskDefinition = new TaskDefinition("taskName",
				"timestamp --format=\"yyyy-MM-dd HH:mm:ss\"");

		String reversedDsl = new TaskDefinitionToDslConverter().toDsl(taskDefinition);

		// Note: after the revers DSL, double quotes have eroded to single quotes
		assertNotEquals(taskDefinition.getDslText(), reversedDsl);
		assertEquals("timestamp --format='yyyy-MM-dd HH:mm:ss'", reversedDsl);

	}

	@Test
	public void doubleQuotedArgumentTests2() {
		TaskDefinition taskDefinition = new TaskDefinition("taskName",
				"t1:timestamp --format=\"yyyy dd\" && t2:timestamp --format=\"yyyy MM\"");

		String reversedDsl = new TaskDefinitionToDslConverter().toDsl(taskDefinition);

		// Note: after the revers DSL, double quotes have eroded to single quotes
		assertNotEquals(taskDefinition.getDslText(), reversedDsl);
		assertEquals("t1:timestamp --format='yyyy dd' && t2:timestamp --format='yyyy MM'", reversedDsl);

	}

	@Test
	public void testPropertyAutoQuotes() {
		TaskDefinition taskDefinition = new TaskDefinition("taskName", "timestamp");
		TaskDefinition td2 = TaskDefinition.TaskDefinitionBuilder.from(taskDefinition)
				.setProperty("p1", "a b")
				.setProperty("p2", "'c d'")
				.setProperty("p3", "ef")
				.setProperty("p4", "'i' 'j'")
				.setProperty("p5", "\"k l\"")
				.build();
		assertEquals("timestamp --p1='a b' --p2=\"'c d'\" --p3=ef --p4=\"'i' 'j'\" --p5=\"k l\"",
				new TaskDefinitionToDslConverter().toDsl(td2));
	}

	private void reverseDslTest(String dslText) {

		TaskDefinition taskDefinition = new TaskDefinition("taskName", dslText);

		assertEquals(taskDefinition.getDslText(),
				new TaskDefinitionToDslConverter().toDsl(taskDefinition));
	}

}
