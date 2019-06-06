/*
 * Copyright 2019 the original author or authors.
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

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.connection.DockerMachine;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.dataflow.rest.client.DataFlowTemplate;
import org.springframework.cloud.dataflow.rest.client.dsl.DeploymentPropertiesBuilder;
import org.springframework.cloud.dataflow.rest.client.dsl.Stream;
import org.springframework.cloud.dataflow.rest.client.dsl.StreamDefinition;
import org.springframework.cloud.dataflow.rest.resource.AppInstanceStatusResource;
import org.springframework.cloud.dataflow.rest.resource.AppStatusResource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Christian Tzolov
 **/
@RunWith(SpringRunner.class)
public class DockerComposeIT {

	public static final String DOCKER_COMPOSE_PATH = "docker-compose.yml"; //src/main/resources/docker-compose.yml

	public static final String DEPLOYED = "deployed";
	public static final String DELETED = "deleted";
	public static final String UNDEPLOYED = "undeployed";
	public static final String DEPLOYING = "deploying";

	// TODO parametrize DATAFLOW_VERSION and SKIPPER_VERSION
	private static DockerMachine dockerMachine = DockerMachine.localMachine()
			//.withAdditionalEnvironmentVariable("DATAFLOW_VERSION", "2.1.0.RELEASE")
			.withAdditionalEnvironmentVariable("DATAFLOW_VERSION", "2.2.0.BUILD-SNAPSHOT")
			.withAdditionalEnvironmentVariable("SKIPPER_VERSION", "2.0.1.RELEASE")
			.build();

	@ClassRule
	public static DockerComposeRule docker = DockerComposeRule.builder()
			.file(DOCKER_COMPOSE_PATH)
			.machine(dockerMachine)
			.saveLogsTo("target/dockerLogs/dockerComposeRuleTest")
			.waitingForService("dataflow-server", HealthChecks.toRespond2xxOverHttp(9393,
					(port) -> port.inFormat("http://$HOST:$EXTERNAL_PORT")))
			.waitingForService("skipper-server", HealthChecks.toRespond2xxOverHttp(7577,
					(port) -> port.inFormat("http://$HOST:$EXTERNAL_PORT")))
			.pullOnStartup(true)
			.build();

	private DataFlowTemplate dataFlowOperations;

	private RuntimeApplicationHelper runtimeApps;

	@Before
	public void before() {
		dataFlowOperations = new DataFlowTemplate(URI.create("http://localhost:9393"));
		runtimeApps = new RuntimeApplicationHelper(dataFlowOperations);
	}

	@Test
	public void testFeatureInfo() {
		assertTrue(dataFlowOperations.aboutOperation().get().getFeatureInfo().isGrafanaEnabled());
		assertTrue(dataFlowOperations.aboutOperation().get().getFeatureInfo().isAnalyticsEnabled());
		assertTrue(dataFlowOperations.aboutOperation().get().getFeatureInfo().isStreamsEnabled());
		assertTrue(dataFlowOperations.aboutOperation().get().getFeatureInfo().isTasksEnabled());
	}

	@Test
	public void testApps() {
		assertThat(dataFlowOperations.appRegistryOperations().list().getMetadata().getTotalElements(),
				greaterThanOrEqualTo(68L));
	}

	@Test
	public void testStream() {

		StreamDefinition streamDefinition = Stream.builder(dataFlowOperations)
				.name("ticktock")
				.definition("time | log --log.expression='TICKTOCK - TIMESTAMP: '.concat(payload)")
				.create();

		// DEPLOY
		Stream stream = streamDefinition.deploy(new DeploymentPropertiesBuilder()
				.put("app.*.logging.file", "${PID}-test.log")
				.put("app.*.endpoints.logfile.sensitive", "false")
				// Specific to Boot 2.x applications, also allows access without authentication
				.put("app.*.management.endpoints.web.exposure.include", "*")
				.put("app.*.spring.cloud.streamapp.security.enabled", "false")
				.build());

		assertThat(stream.getStatus(), is(DEPLOYING));
		Wait.on(stream).until(s -> s.getStatus().equals(DEPLOYING) == false); //E.g. blocks until in `deploying` state.

		assertThat(stream.getStatus(), is(DEPLOYED));

		Wait.on(stream).until(s -> getAppLog(s.getName(), "log").contains("TICKTOCK - TIMESTAMP:"));

		assertThat(stream.history().size(), is(1));
		assertThat(stream.history().get(1), is(DEPLOYED));

		// UPDATE
		stream.update(new DeploymentPropertiesBuilder()
				.put("app.log.log.expression", "'Updated TICKTOCK - TIMESTAMP: '.concat(payload)")
				.put("app.*.management.endpoints.web.exposure.include", "*")
				.build());


		Wait.on(stream).withTimeout(Duration.ofMinutes(5)).until(s -> s.getStatus().equals(DEPLOYED));
		assertThat(stream.getStatus(), is(DEPLOYED));

		Wait.on(stream).until(s -> getAppLog(s.getName(), "log").contains("Updated TICKTOCK - TIMESTAMP:"));

		assertThat(stream.history().size(), is(2));
		assertThat(stream.history().get(1), is(DELETED));
		assertThat(stream.history().get(2), is(DEPLOYED));

		// ROLLBACK
		stream.rollback(0);

		Wait.on(stream).until(s -> s.getStatus().equals(DEPLOYED));
		assertThat(stream.getStatus(), is(DEPLOYED));

		Wait.on(stream).until(s -> getAppLog(s.getName(), "log").contains("TICKTOCK - TIMESTAMP:"));

		assertThat(stream.history().size(), is(3));
		assertThat(stream.history().get(1), is(DELETED));
		assertThat(stream.history().get(2), is(DELETED));
		assertThat(stream.history().get(3), is(DEPLOYED));

		// UNDEPLOY
		stream.undeploy();

		Wait.on(stream).until(s -> s.getStatus().equals(UNDEPLOYED));
		assertThat(stream.getStatus(), is(UNDEPLOYED));

		assertThat(stream.history().size(), is(3));
		assertThat(stream.history().get(1), is(DELETED));
		assertThat(stream.history().get(2), is(DELETED));
		assertThat(stream.history().get(3), is(DELETED));

		// DESTROY
		assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements(), is(1L));

		stream.destroy();

		assertThat(dataFlowOperations.streamOperations().list().getMetadata().getTotalElements(), is(0L));
	}

	/**
	 * Extract the Logs from the first instance of an application in a stream.
	 * @param streamName Name of the stream where the application is defined.
	 * @param appName Name of the applications to retrieve the applicationInstanceLogs for.
	 * @return Returns the applicationInstanceLogs of the first instance of the specified application.
	 */
	private String getAppLog(String streamName, String appName) {
		return runtimeApps.applicationInstanceLogs(streamName, appName).values().iterator().next();
	}

	// TODO add Task smoking tests
	@Test
	public void testTasks() {
		assertTrue(true);
	}

	// TODO move the utilities in standalone classes?

	/**
	 * Utilities that helps to blocks execution until a predicate is satisfied or throws a IllegalStateException if
	 * the preconfigured timeout duration is exceeded. The until method, repeatedly tests the predicate
	 * in pauseDuration intervals. For instance:
	 *  <pre>
	 *     {@code
	 *     Wait.on(stream).withTimeout(Duration.ofMinutes(5)).until(s -> s.getStatus().equals("deployed"));
	 *     }
	 * </pre>
	 *
	 * @param <T> An argument passed to the predicate on every test.
	 */
	public static class Wait<T> {

		/**
		 * An argument passed to the predicate on every test.
		 */
		private T target;

		/**
		 * Total time to wait until the predicate is satisfied. When the timeoutDuration is exceeded
		 * an {@link IllegalStateException} is thrown.
		 */
		private Duration timeoutDuration = Duration.ofMinutes(5);

		/**
		 * Time for waiting between two consecutive predicate tests.
		 */
		private Duration pauseDuration = Duration.ofSeconds(10);

		private Wait(T target) {
			this.target = target;
		}

		public static <T> Wait<T> on(T t) {
			return new Wait<>(t);
		}

		public Wait<T> withTimeout(Duration timeout) {
			this.timeoutDuration = timeout;
			return this;
		}

		public Wait<T> withPause(Duration timeout) {
			this.pauseDuration = timeout;
			return this;
		}

		/**
		 * Blocks until the wait-predicate is satisfied or throws a IllegalStateException if the timeoutDuration expires.
		 * Repeatedly tests the predicate in pauseDuration intervals.
		 *
		 * TODO: for Java9 consider using CompletableFuture instead.
		 *
		 * @param waitPredicate Condition which when satisfied (e.g. #test() returns true) unblocks the call.
		 */
		public void until(Predicate<T> waitPredicate) {

			final long timeout = System.currentTimeMillis() + this.timeoutDuration.toMillis();

			while (System.currentTimeMillis() < timeout) {
				if (waitPredicate.test(this.target)) {
					return;
				}
				try {
					TimeUnit.SECONDS.sleep(this.pauseDuration.getSeconds());
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(e.getMessage(), e);
				}
			}
			throw new IllegalStateException("Wait timeout after " + this.timeoutDuration + " seconds");
		}
	}

	/**
	 * Helper class to retrieve runtime information form DataFlow server.
	 */
	public static class RuntimeApplicationHelper {

		private final Logger logger = LoggerFactory.getLogger(RuntimeApplicationHelper.class);

		private RestTemplate restTemplate = new RestTemplate();

		private DataFlowTemplate dataFlowOperations;

		public RuntimeApplicationHelper(DataFlowTemplate dataFlowOperations) {
			this.dataFlowOperations = dataFlowOperations;
		}

		/**
		 *
		 * @return
		 */
		public Map<String, Map<String, String>> appInstanceAttributes() {

			Map<String, Map<String, String>> appInstanceAttributes = new HashMap<>();

			Iterable<AppStatusResource> apps = dataFlowOperations.runtimeOperations().status();
			for (AppStatusResource app : apps) {
				Iterable<AppInstanceStatusResource> instances = app.getInstances();
				for (AppInstanceStatusResource instance : instances) {
					Map<String, String> attrs = instance.getAttributes();
					appInstanceAttributes.put(instance.getInstanceId(), attrs);
				}
			}

			return appInstanceAttributes;
		}

		/**
		 * For given stream name and application name retrieves the logs for application instances (if more then one)
		 * belonging to this application.
		 *
		 * @param streamName DataFlow stream for which the log is retrieved.
		 * @param appName Application inside the stream name for which logs are trieved.
		 * @return Returns a map of app instance GUIDs and their Log content. A single entry per app instance.
		 */
		public Map<String, String> applicationInstanceLogs(String streamName, String appName) {
			return this.appInstanceAttributes().values().stream()
					.filter(v -> v.get("skipper.release.name").equals(streamName))
					.filter(v -> v.get("skipper.application.name").equals(appName))
					.collect(Collectors.toMap(v -> v.get("guid"), v -> getAppInstanceLogContent(v.get("port"))));
		}

		/**
		 * Retrieve the log for an app.
		 * @param port of the application as exposed to the HOST
		 * @return String containing the contents of the log or 'null' if not found.
		 */
		private String getAppInstanceLogContent(String port) {
			String logContent = null;
			String logFileUrl = String.format("http://localhost:%s/actuator/logfile", port);
			try {
				logContent = restTemplate.getForObject(logFileUrl, String.class);
				if (logContent == null) {
					logger.warn("Unable to retrieve logfile from '" + logFileUrl);
				}
			}
			catch (Exception e) {
				logger.warn("Error while trying to access logfile from '" + logFileUrl + "' due to : " + e);
			}
			return logContent;
		}
	}
}
