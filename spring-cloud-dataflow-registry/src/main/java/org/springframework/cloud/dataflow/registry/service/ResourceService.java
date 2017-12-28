/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.registry.service;

import java.net.URI;

import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * @author Christian Tzolov
 */
public class ResourceService {

	private MavenProperties mavenProperties;

	private ResourceLoader resourceLoader;

	public ResourceService(MavenProperties mavenProperties, ResourceLoader resourceLoader) {
		this.mavenProperties = mavenProperties;
		this.resourceLoader = resourceLoader;
	}

	public Resource getResource(String uriString) {
		return ResourceUtils.getResource(uriString, this.mavenProperties);
	}

	public String getResourceVersion(String uriString) {
		return ResourceUtils.getResourceVersion(uriString, this.mavenProperties);
	}

	public String getResourceVersion(Resource resource) {
		return ResourceUtils.getResourceVersion(resource);
	}

	public String getResourceWithoutVersion(Resource resource) {
		return ResourceUtils.getResourceWithoutVersion(resource);
	}

	public Resource getAppMetadataResource(URI metadataUri) {
		return metadataUri != null ? this.resourceLoader.getResource(metadataUri.toString()) : null;
	}
}
