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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.dataflow.core.DataFlowPropertyKeys;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(prefix = AtlasServerProperties.PREFIX)
public class AtlasServerProperties {
	public static final String PREFIX = DataFlowPropertyKeys.PREFIX + "metrics.atlas.server";

	private boolean enabled = false;

	private Resource config = new DefaultResourceLoader().getResource("classpath:/memory.conf");

	public Resource getConfig() {
		return config;
	}

	public void setConfig(Resource config) {
		this.config = config;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
