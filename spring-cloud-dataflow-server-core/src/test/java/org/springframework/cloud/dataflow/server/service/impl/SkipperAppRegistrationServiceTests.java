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
package org.springframework.cloud.dataflow.server.service.impl;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.server.config.features.FeaturesProperties;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.dataflow.server.repository.StreamDefinitionRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestDependencies.class)
@TestPropertySource(properties = { "spring.main.banner-mode=off",
		FeaturesProperties.FEATURES_PREFIX + "." + FeaturesProperties.SKIPPER_ENABLED + "=true" })
public class SkipperAppRegistrationServiceTests {

	@Autowired
	private StreamDefinitionRepository streamDefinitionRepository;

	@Autowired
	private AppRegistryService appRegistryService;

	@Test
	public void selectDefaultVersionAfterDelete() throws URISyntaxException {

		registerApp("time", ApplicationType.source, "maven://org.springframework.cloud.stream.app:time-source-rabbit", "1.2.0.RELEASE");
		registerApp("time", ApplicationType.source, "maven://org.springframework.cloud.stream.app:time-source-rabbit", "1.3.0.RELEASE");
		registerApp("time", ApplicationType.source, "maven://org.springframework.cloud.stream.app:time-source-rabbit", "1.1.1.RELEASE");
		registerApp("time", ApplicationType.source, "maven://org.springframework.cloud.stream.app:time-source-rabbit", "1.1.1.BUILD-SNAPSHOT");
		registerApp("time", ApplicationType.source, "maven://org.springframework.cloud.stream.app:time-source-rabbit", "1.1.0.RELEASE");


		registerApp("log", ApplicationType.sink, "maven://org.springframework.cloud.stream.app:log-source-rabbit", "1.2.0.RELEASE");

		// Create stream
//		StreamDefinition streamDefinition = new StreamDefinition("ticktock", "time | log");
//		this.streamDefinitionRepository.save(streamDefinition);
//
//		List<AppRegistration> apps = appRegistryService.findAll();
//		for (AppRegistration app : apps) {
//			appRegistryService.delete(app.getName(), app.getType(), app.getVersion());
//		}
//
//		streamDefinitionRepository.deleteAll();


		appRegistryService.setDefaultApp("time", ApplicationType.source, "1.2.0.RELEASE");

		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.2.0.RELEASE");

		appRegistryService.delete("time", ApplicationType.source, "1.2.0.RELEASE");

		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.3.0.RELEASE");

		appRegistryService.delete("time", ApplicationType.source, "1.3.0.RELEASE");

		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.1.1.RELEASE");


		appRegistryService.delete("time", ApplicationType.source, "1.1.1.RELEASE");

		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.1.1.BUILD-SNAPSHOT");

		appRegistryService.delete("time", ApplicationType.source, "1.1.1.BUILD-SNAPSHOT");
		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.1.0.RELEASE");

		appRegistryService.delete("time", ApplicationType.source, "1.1.0.RELEASE");
		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source).getVersion())
				.isEqualTo("1.0.0.BUILD-SNAPSHOT");

//		assertThat(appRegistryService.getDefaultApp("time", ApplicationType.source)).isNull();
	}

	private void registerApp(String name, ApplicationType type, String uriPrefix, String version) throws URISyntaxException {
		String uri = uriPrefix + ":" + version;
		appRegistryService.save(name, type, version, new URI(uri), null);

	}

}
