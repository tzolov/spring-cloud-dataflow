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

package org.springframework.cloud.dataflow.server.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.dataflow.server.config.MetricsProperties;
import org.springframework.cloud.dataflow.server.controller.support.ApplicationsMetrics;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Metrics Controller that retrieves the Stream metrics from an Atlas Server backend instead for the Metric Collector.
 * Requires the --spring.cloud.dataflow.metrics.atlas.server.enabled=true property to be activated. Use the
 * --spring.cloud.dataflow.metrics.collector.uri=http://localhost:7101 property to set the path to the Atlas server.
 *
 * @author Christian Tzolov
 */
public class AtlasMetricsController extends AbstractMetricsController {

	private static String ATLAS_METRIC_ULR_TEMPLATE = "/api/v1/graph?q=name,spring.integration.send,:eq," +
			"type,channel,:eq,:and," +
			"aname,nullChannel,:eq,:not,:and," +
			"result,success,:eq,:and," +
			"statistic,count,:eq,:and," +
			"(,stream,aname,applicationName,applicationGuid,),:by," +
			"$applicationName+(+$aname+)+$applicationGuid,:legend&s=e-1m&l=0&format=json";

	private final static List<ApplicationsMetrics> EMPTY_RESPONSE = new ArrayList<>();
	private static Log logger = LogFactory.getLog(AtlasMetricsController.class);
	private final RestTemplate restTemplate;
	private final Map<String, ApplicationsMetrics> streamMap;
	private MetricsProperties metricsProperties;

	/**
	 * Instantiates a new metrics controller.*
	 */
	public AtlasMetricsController(MetricsProperties metricsProperties) {
		this.metricsProperties = metricsProperties;

		this.streamMap = new ConcurrentHashMap<>();

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
		MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter();
		messageConverter.setSupportedMediaTypes(MediaType.parseMediaTypes("application/json"));
		messageConverter.setObjectMapper(mapper);
		this.restTemplate = new RestTemplate(Arrays.asList(messageConverter));
	}

	@RequestMapping(method = RequestMethod.GET)
	public List<ApplicationsMetrics> list() {
		try {
			Map<String, Object> rawMetrics = restTemplate.getForObject(metricsProperties.getCollector().getUri() + ATLAS_METRIC_ULR_TEMPLATE, Map.class);
			List<Map<String, String>> metrics = (List<Map<String, String>>) rawMetrics.get("metrics");
			List<List<Double>> values = (List<List<Double>>) rawMetrics.get("values");
			return getAtlasStreamMetrics(metrics, values);
		}
		catch (ResourceAccessException e) {
			logger.warn(e);
			return EMPTY_RESPONSE;
		}
	}

	private List<ApplicationsMetrics> getAtlasStreamMetrics(List<Map<String, String>> metrics, List<List<Double>> values) {

		for (int i = 0; i < metrics.size(); i++) {
			try {
				Map<String, String> atlasMetric = metrics.get(i);

				String streamName = atlasMetric.get("stream");
				ApplicationsMetrics applicationsMetrics = streamMap.get(streamName);
				if (applicationsMetrics == null) {
					applicationsMetrics = new ApplicationsMetrics();
					applicationsMetrics.setName(streamName);
					applicationsMetrics.setApplications(new ArrayList<>());
					streamMap.put(streamName, applicationsMetrics);
				}

				String applicationName = atlasMetric.get("applicationName");
				String applicationGuid = atlasMetric.get("applicationGuid");
				ApplicationsMetrics.Application app = findApplication(applicationsMetrics.getApplications(), applicationName);
				if (app == null) {
					app = new ApplicationsMetrics.Application();
					app.setName(applicationName);
					app.setAggregateMetrics(new ArrayList<>());
					app.setInstances(new ArrayList<>());
					applicationsMetrics.getApplications().add(app);
				}

				int instanceIndex = Integer.valueOf(atlasMetric.get("instanceIndex"));
				ApplicationsMetrics.Instance appInstance = findInstance(app.getInstances(), instanceIndex);
				if (appInstance == null) {
					appInstance = new ApplicationsMetrics.Instance();
					appInstance.setGuid(applicationGuid);
					appInstance.setIndex(instanceIndex);
					appInstance.setMetrics(new ArrayList<>());
					appInstance.setProperties(createInstanceProperties(atlasMetric));
					app.getInstances().add(appInstance);
				}

				String channelName = atlasMetric.get("aname");
				String appInstanceMetricName = "integration.channel." + channelName + ".send.mean";

				ApplicationsMetrics.Metric appMetric = findInstanceMetric(appInstance.getMetrics(), appInstanceMetricName);
				if (appMetric == null) {
					appMetric = new ApplicationsMetrics.Metric();
					appMetric.setName(appInstanceMetricName);
					appInstance.getMetrics().add(appMetric);
				}
				Double value = values.get(0).get(i);
				appMetric.setValue(value);
			}
			catch (Throwable throwable) {
				logger.warn(throwable);
			}
		}

		for (ApplicationsMetrics applicationsMetrics : streamMap.values()) {
			computeAggregations(applicationsMetrics);
		}

		return streamMap.isEmpty() ? EMPTY_RESPONSE : streamMap.values().stream().collect(Collectors.toList());
	}

	private Map<String, Object> createInstanceProperties(Map<String, String> atlasMetric) {
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.application.index", atlasMetric.get("instanceIndex"));
		properties.put("spring.cloud.application.guid", atlasMetric.get("applicationGuid"));
		properties.put("spring.cloud.dataflow.stream.app.type", atlasMetric.get("applicationType"));
		properties.put("spring.cloud.dataflow.stream.name", atlasMetric.get("stream"));
		properties.put("spring.cloud.application.group", atlasMetric.get("stream"));
		properties.put("spring.cloud.dataflow.stream.app.label", atlasMetric.get("applicationName"));
		properties.put("spring.cloud.stream.channel.name", atlasMetric.get("aname"));

		return properties;
	}

	private void computeAggregations(ApplicationsMetrics applicationsMetrics) {
		for (ApplicationsMetrics.Application app : applicationsMetrics.getApplications()) {

			try {
				List<ApplicationsMetrics.Metric> list = app.getInstances().stream()
						.map(instance -> instance.getMetrics()).flatMap(metrics -> metrics.stream())
						.filter(metric -> metric.getName().matches("integration\\.channel\\.(\\w*)\\.send\\.mean"))
						.collect(Collectors.groupingBy(m -> m.getName(),
								Collectors.summingDouble(m -> (Double) m.getValue())))
						.entrySet().stream().map(entry -> toMetric(entry.getKey(), entry.getValue()))
						.collect(Collectors.toList());

				app.setAggregateMetrics(list);
			}
			catch (Throwable throwable) {
				logger.warn(throwable);
			}
		}
	}

	private ApplicationsMetrics.Metric toMetric(String key, Double value) {
		ApplicationsMetrics.Metric metric = new ApplicationsMetrics.Metric();
		metric.setName(key);
		metric.setValue(value);
		return metric;
	}

	private ApplicationsMetrics.Metric findInstanceMetric(List<ApplicationsMetrics.Metric> appInstanceMetrics, String metricName) {
		for (ApplicationsMetrics.Metric m : appInstanceMetrics) {
			if (m.getName().equals(metricName)) {
				return m;
			}
		}
		return null;
	}

	private ApplicationsMetrics.Application findApplication(List<ApplicationsMetrics.Application> apps, String appName) {
		for (ApplicationsMetrics.Application app : apps) {
			if (app.getName().equals(appName)) {
				return app;
			}
		}
		return null;
	}

	private ApplicationsMetrics.Instance findInstance(List<ApplicationsMetrics.Instance> instances, int instanceIndex) {
		for (ApplicationsMetrics.Instance instance : instances) {
			if (instance.getIndex() == instanceIndex) {
				return instance;
			}
		}
		return null;
	}

}
