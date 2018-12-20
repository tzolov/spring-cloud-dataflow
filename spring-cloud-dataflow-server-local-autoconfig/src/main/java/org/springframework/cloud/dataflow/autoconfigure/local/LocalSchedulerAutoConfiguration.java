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
package org.springframework.cloud.dataflow.autoconfigure.local;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.cloud.dataflow.server.config.features.SchedulerConfiguration;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @author Mark Pollack
 */
@Configuration
@Conditional({ SchedulerConfiguration.SchedulerConfigurationPropertyChecker.class,
		LocalSchedulerAutoConfiguration.OnNotKubernetesPlatform.class })
@Profile("!cloud") // Server not deployed on CF
public class LocalSchedulerAutoConfiguration {

	static class OnNotKubernetesPlatform extends NoneNestedConditions {

		public OnNotKubernetesPlatform(ConfigurationPhase configurationPhase) {
			super(configurationPhase);
		}

		@ConditionalOnProperty(name = "kubernetes.service.host")
		static class OnKubernetesPlatform {
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public Scheduler localScheduler() {
		return new Scheduler() {
			@Override
			public void schedule(ScheduleRequest scheduleRequest) {
				throw new UnsupportedOperationException("Interface is not implemented for schedule method.");
			}

			@Override
			public void unschedule(String scheduleName) {
				throw new UnsupportedOperationException("Interface is not implemented for unschedule method.");
			}

			@Override
			public List<ScheduleInfo> list(String taskDefinitionName) {
				throw new UnsupportedOperationException("Interface is not implemented for list method.");
			}

			@Override
			public List<ScheduleInfo> list() {
				throw new UnsupportedOperationException("Interface is not implemented for list method.");
			}
		};
	}
}
