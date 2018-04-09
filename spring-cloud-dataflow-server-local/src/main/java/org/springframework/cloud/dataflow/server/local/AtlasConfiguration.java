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

package org.springframework.cloud.dataflow.server.local;

import java.io.File;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.atlas.config.ConfigManager;
import com.netflix.iep.guice.GuiceHelper;
import com.netflix.iep.service.ServiceManager;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Configuration;

@Configuration
public class AtlasConfiguration {
	private Logger logger = LoggerFactory.getLogger(AtlasConfiguration.class);
	private GuiceHelper guice = new GuiceHelper();

	/**
	 * Almost identical logic is found in atlas-standalone
	 */
	@PostConstruct
	public void startAtlas() {
		try {
			// Start an embedded Atlas server at a port governed by the provided Atlas config, or 7101 by default
			loadAdditionalConfigFiles("/Users/ctzolov/Dev/projects/scdf/spring-cloud-dataflow/spring-cloud-dataflow-server-local/src/main/resources/memory.conf");

			List<Module> modules = GuiceHelper.getModulesUsingServiceLoader();

			modules.add(new AbstractModule() {
				@Override
				protected void configure() {
					bind(Registry.class).toInstance(Spectator.globalRegistry());
					bind(Config.class).toInstance(ConfigManager.current());
				}
			});

			guice.start(modules);

			// Ensure that service manager instance has been created
			guice.getInjector().getInstance(ServiceManager.class);

			guice.addShutdownHook();
		}
		catch (Throwable t) {
			logger.error("server failed to start, shutting down", t);
		}
	}

	private void loadAdditionalConfigFiles(String... files) {
		for (String path : files) {
			logger.info("loading config file: {}", path);
			File file = new File(path);
			Config c = file.exists() ?
					ConfigFactory.parseFileAnySyntax(file) :
					ConfigFactory.parseResourcesAnySyntax(path);
			ConfigManager.update(c);
		}
	}

	@PreDestroy
	public void stopAtlasIfNecessary() throws Exception {
		guice.shutdown();
	}
}
