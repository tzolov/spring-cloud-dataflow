/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.cloud.dataflow.integration.test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.rest.client.dsl.task.Task;
import org.springframework.cloud.dataflow.rest.client.dsl.task.TaskBuilder;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionResource;
import org.springframework.cloud.dataflow.rest.resource.TaskExecutionStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnableConfigurationProperties({ IntegrationTestProperties.class })
class DataFlowPerformanceIT extends DataFlowIT {

	private static final Logger logger = LoggerFactory.getLogger(DataFlowPerformanceIT.class);

	@Test
	public void taskPerformanceTest() {
		logger.info("task-composed-task-runner-test");

		TaskBuilder taskBuilder = Task.builder(dataFlowOperations);

		String ctrTaskDefinition = "a: timestamp && b:timestamp";
		int numberOfCtrTasks = 100;
		final Map<Long, Task> launchedTasks = new ConcurrentHashMap<>();

		try {

			for (int i = 0; i < numberOfCtrTasks; i++) {
				Task task = taskBuilder
						.name(randomTaskName())
						.definition(ctrTaskDefinition)
						.description("Test composedTask")
						.build();

				assertThat(task.composedTaskChildTasks().size()).isEqualTo(2);

				long launchId = task.launch(composedTaskLaunchArguments());

				launchedTasks.put(launchId, task);
			}

			assertThat(launchedTasks).hasSize(numberOfCtrTasks);

			Map<String, Long> taskDurations = new HashMap<>();

			// Wait until all tasks complete
			Awaitility.await().until(() -> {
				AtomicInteger numberOfCompletedTasks = new AtomicInteger(0);

				Iterator<Map.Entry<Long, Task>> iterator = launchedTasks.entrySet().iterator();

				while (iterator.hasNext()) {
					Map.Entry<Long, Task> entry = iterator.next();

					Long launchId = entry.getKey();
					Task task = entry.getValue();

					if (task.executionStatus(launchId) == TaskExecutionStatus.COMPLETE) {
						iterator.remove();

						numberOfCompletedTasks.incrementAndGet();

						assertThat(task.executions().size()).isEqualTo(1);
						assertThat(task.executionStatus(launchId)).isEqualTo(TaskExecutionStatus.COMPLETE);
						assertThat(task.execution(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);

						// Get task duration in ms.
						TaskExecutionResource parentTaskExecution = task.execution(launchId).get();
						long parentTaskDurationMs = parentTaskExecution.getEndTime().getTime() - parentTaskExecution.getStartTime().getTime();
						taskDurations.put(task.getTaskName(), parentTaskDurationMs);

						task.composedTaskChildTasks().forEach(childTask -> {
							assertThat(childTask.executions().size()).isEqualTo(1);
							assertThat(childTask.executionByParentExecutionId(launchId).get().getExitCode()).isEqualTo(EXIT_CODE_SUCCESS);
						});

						task.executions().forEach(execution -> assertThat(execution.getExitCode()).isEqualTo(EXIT_CODE_SUCCESS));
					}
				}

				return numberOfCompletedTasks.get() == numberOfCtrTasks;
			});

			long durationSum = taskDurations.values().stream()
					.mapToLong(Long::longValue)
					.sum();

			System.out.println("Avg duration: " + durationSum / taskDurations.size());
		}
		finally {
			dataFlowOperations.taskOperations().destroyAll();
		}

		assertThat(taskBuilder.allTasks().size()).isEqualTo(0);
	}
}
