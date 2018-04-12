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

package org.springframework.cloud.dataflow.server.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

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

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;

/**
 * Manages the lifecycle of the embedded Atlas server. It start and stop the server. Server is configured by provided
 * *.conf file. Configuration defaults to memory.conf
 *
 * @author Christian Tzolov
 */
public class AtlasServerFactoryBean implements FactoryBean<ServiceManager>, InitializingBean, DisposableBean {

	private Logger logger = LoggerFactory.getLogger(AtlasServerFactoryBean.class);
	private GuiceHelper guice = new GuiceHelper();
	private ServiceManager serviceManager;
	private AtlasServerProperties atlasServerProperties;

	public AtlasServerFactoryBean(AtlasServerProperties atlasServerProperties) {
		this.atlasServerProperties = atlasServerProperties;
	}

	@Override
	public void destroy() throws Exception {
		guice.shutdown();
		logger.info("Embedded Atlas server stopped");
	}

	@Override
	public ServiceManager getObject() {
		return serviceManager;
	}

	@Override
	public Class<?> getObjectType() {
		return ServiceManager.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// Start an embedded Atlas server at a port governed by the provided Atlas config, or 7101 by default
		loadAdditionalConfigFiles(this.atlasServerProperties.getConfig());

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
		serviceManager = guice.getInjector().getInstance(ServiceManager.class);
		guice.addShutdownHook();

		logger.info("Embedded Atlas server started");
	}

	private void loadAdditionalConfigFiles(Resource configFilePath) throws IOException {
		logger.info("loading embedded Atlas config file: {}", configFilePath);
		Config c = ConfigFactory.parseReader(
				new InputStreamReader(configFilePath.getInputStream()));
		ConfigManager.update(c);
	}
}
