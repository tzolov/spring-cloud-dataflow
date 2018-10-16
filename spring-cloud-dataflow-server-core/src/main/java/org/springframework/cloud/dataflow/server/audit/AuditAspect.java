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

package org.springframework.cloud.dataflow.server.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.core.StreamDefinition;
import org.springframework.cloud.dataflow.registry.domain.AppRegistration;
import org.springframework.cloud.dataflow.server.audit.domain.AuditActionType;
import org.springframework.cloud.dataflow.server.audit.domain.AuditOperationType;
import org.springframework.cloud.dataflow.server.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.server.audit.service.AuditServiceUtils;
import org.springframework.cloud.skipper.domain.PackageIdentifier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;

/**
 * @author Christian Tzolov
 */
@Aspect
public class AuditAspect {

	private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);

	@Autowired
	protected AuditRecordService auditRecordService;

	private ThreadLocalAuditContextHolder auditContextHolder = new ThreadLocalAuditContextHolder();
	protected AuditServiceUtils auditServiceUtils = new AuditServiceUtils();

	public AuditAspect() {
		this.auditContextHolder = new ThreadLocalAuditContextHolder();
		this.auditServiceUtils = new AuditServiceUtils();
	}

	//public AuditAspect(AuditRecordService auditRecordService) {
	//	this.auditRecordService = auditRecordService;
	//	this.auditContextHolder = new ThreadLocalAuditContextHolder();
	//	this.auditServiceUtils = new AuditServiceUtils();
	//}

	// --------------------------------------------------------------
	// App - Classic
	// --------------------------------------------------------------
	@Around("execution(* org.springframework.cloud.dataflow.server.controller.AppRegistryController.registerAll(..)) " +
			"&& args(pageable, pagedResourcesAssembler, uri, apps, force)")
	public Object registerAllAppsClassic(ProceedingJoinPoint joinPoint,
			Pageable pageable, PagedResourcesAssembler<AppRegistration> pagedResourcesAssembler,
			String uri, Properties apps, boolean force) throws Throwable {

		logger.info("AOP: registerAllAppsClassic");

		Object result = joinPoint.proceed(joinPoint.getArgs());

		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.CREATE, "-",
				String.format("Register Bulk Apps %s", uri));

		return result;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.controller.AppRegistryController.register(..)) " +
			"&& args(type, name, uri, metadataUri, force)")
	public void registerAppClassic(ProceedingJoinPoint joinPoint,
			ApplicationType type, String name, String uri, String metadataUri, boolean force) throws Throwable {

		logger.info("AOP: registerAppClassic");

		joinPoint.proceed(joinPoint.getArgs());
		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.CREATE, "-",
				String.format("Register App %s, %s, %s, %s, %s", type, name, uri, metadataUri, force));
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.controller.AppRegistryController.unregister(..)) " +
			"&& args(type,name)")
	public void unregisterAppClassic(ProceedingJoinPoint joinPoint, ApplicationType type, String name) throws Throwable {
		joinPoint.proceed(joinPoint.getArgs());
		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.DELETE, "-",
				String.format("Unregister App type: %s, name: %s", type, name));
	}

	// --------------------------------------------------------------
	// App - Skipper
	// --------------------------------------------------------------
	@Around("execution(* org.springframework.cloud.dataflow.server.controller.SkipperAppRegistryController.register(..)) " +
			"&& args(type, name, version, uri, metadataUri, force)")
	public void registerAppSkipper(ProceedingJoinPoint joinPoint,
			ApplicationType type, String name, String version, String uri, String metadataUri, boolean force) throws Throwable {

		logger.info("AOP: registerAppSkipper");

		joinPoint.proceed(joinPoint.getArgs());
		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.CREATE, "-",
				String.format("Register App type: %s, name: %s, version: %s, uri: %s", type, name, version, uri));
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.controller.SkipperAppRegistryController.registerAll(..)) " +
			"&& args(pageable,pagedResourcesAssembler,uri,apps,force)")
	public Object registerAllSkipper(ProceedingJoinPoint joinPoint,
			Pageable pageable, PagedResourcesAssembler<AppRegistration> pagedResourcesAssembler,
			String uri, Properties apps, boolean force) throws Throwable {

		logger.info("AOP: registerAllSkipper");

		Object result = joinPoint.proceed(joinPoint.getArgs());

		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.CREATE, "-",
				String.format("Register All Apps uri: %s", uri));
		return result;
	}


	@Around("execution(* org.springframework.cloud.dataflow.server.controller.SkipperAppRegistryController.unregister(..)) " +
			"&& args(type,name,version)")
	public void unregisterAppSkipper(ProceedingJoinPoint joinPoint, ApplicationType type, String name, String version) throws Throwable {

		logger.info("AOP: unregisterAppSkipper");

		joinPoint.proceed(joinPoint.getArgs());
		auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.APP_REGISTRATION, AuditActionType.DELETE, "-",
				String.format("Unregister App type: %s, name: %s, version: %s", type, name, version));
	}


	// --------------------------------------------------------------
	// StreamService - All
	// --------------------------------------------------------------
	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.AbstractStreamService.createStream(..)) " +
			"&& args(streamName, dsl, deploy)")
	public Object createStream(ProceedingJoinPoint joinPoint,
			String streamName, String dsl, boolean deploy) throws Throwable {

		logger.info("AOP: createStream");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		auditRecordService.populateAndSaveAuditRecord(AuditOperationType.STREAM, AuditActionType.CREATE,
				streamDefinition.getName(), this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));

		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.AbstractStreamService.deployStream(..)) " +
			"&& args(name, deploymentProperties)")
	public StreamDefinition deployStream(ProceedingJoinPoint joinPoint,
			String name, Map<String, String> deploymentProperties) throws Throwable {

		logger.info("AOP: deployStream");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		auditRecordService.populateAndSaveAuditRecordUsingMapData(AuditOperationType.STREAM, AuditActionType.DEPLOY, name,
				this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition, deploymentProperties));

		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.DefaultSkipperStreamService.doDeployStream(..)) " +
			"&& args(streamDefinition, deploymentProperties)")
	public void doDeployStreamSkipper(ProceedingJoinPoint joinPoint,
			StreamDefinition streamDefinition, Map<String, String> deploymentProperties) throws Throwable {

		logger.info("AOP: doDeployStreamSkipper");

		joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecordUsingMapData(AuditOperationType.STREAM,
				AuditActionType.DEPLOY,
				streamDefinition.getName(), this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition, deploymentProperties));
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.AbstractStreamService.deleteStream(..)) " +
			"&& args(name)")
	public StreamDefinition deleteStream(ProceedingJoinPoint joinPoint,
			String name) throws Throwable {

		logger.info("AOP: deleteStream");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.DELETE, name,
				this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));

		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.AbstractStreamService.deleteAll())")
	public Iterable<StreamDefinition> deleteAllStream(ProceedingJoinPoint joinPoint) throws Throwable {

		logger.info("AOP: deleteAllStream");

		Iterable<StreamDefinition> streamDefinitions = (Iterable<StreamDefinition>) joinPoint.proceed(joinPoint.getArgs());

		for (StreamDefinition streamDefinition : streamDefinitions) {
			this.auditRecordService.populateAndSaveAuditRecord(
					AuditOperationType.STREAM, AuditActionType.DELETE,
					streamDefinition.getName(), this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));
		}

		return streamDefinitions;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.AppDeployerStreamService.undeployStream(..)) " +
			"&& args(streamName)")
	public StreamDefinition undeployStreamClassic(ProceedingJoinPoint joinPoint, String streamName) throws Throwable {

		logger.info("AOP: undeployStreamClassic");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.UNDEPLOY,
				streamDefinition.getName(), this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));


		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.DefaultSkipperStreamService.undeployStream(..)) " +
			"&& args(streamName)")
	public StreamDefinition undeployStreamSkipper(ProceedingJoinPoint joinPoint, String streamName) throws Throwable {

		logger.info("AOP: undeployStreamSkipper");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.UNDEPLOY,
				streamDefinition.getName(), this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));

		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.DefaultSkipperStreamService.updateStreamDefinitionFromReleaseManifest(..)) " +
			"&& args(streamName,releaseManifest)")
	public StreamDefinition updateStreamDefinitionFromReleaseManifestSkipper(ProceedingJoinPoint joinPoint, String streamName, String releaseManifest) throws Throwable {

		logger.info("AOP: updateStreamDefinitionFromReleaseManifestSkipper");

		StreamDefinition streamDefinition = (StreamDefinition) joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecord(
				AuditOperationType.STREAM, AuditActionType.UPDATE, streamName, this.auditServiceUtils.convertStreamDefinitionToAuditData(streamDefinition));

		return streamDefinition;
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.DefaultSkipperStreamService.rollbackStream(..)) " +
			"&& args(streamName,releaseVersion)")
	public void rollbackStreamSkipper(ProceedingJoinPoint joinPoint, String streamName, int releaseVersion) throws Throwable {

		logger.info("AOP: rollbackStreamSkipper");

		joinPoint.proceed(joinPoint.getArgs());

		this.auditRecordService.populateAndSaveAuditRecord(AuditOperationType.STREAM, AuditActionType.ROLLBACK,
				streamName, "Rollback to version: " + releaseVersion);
	}

	@Around("execution(* org.springframework.cloud.dataflow.server.service.impl.DefaultSkipperStreamService.updateStream(..)) " +
			"&& args(streamName,releaseName,packageIdentifier,updateProperties,force,appNames)")
	public void updateStreamSkipper(ProceedingJoinPoint joinPoint,
			String streamName, String releaseName, PackageIdentifier packageIdentifier,
			Map<String, String> updateProperties, boolean force, List<String> appNames) throws Throwable {

		logger.info("AOP: updateStreamSkipper");

		joinPoint.proceed(joinPoint.getArgs());

		//final String sanatizedUpdateYaml = convertPropertiesToSkipperYaml(streamDefinition,
		//		this.auditServiceUtils.sanitizeProperties(updateProperties));

		final Map<String, Object> auditedData = new HashMap<>(3);
		auditedData.put("releaseName", releaseName);
		auditedData.put("packageIdentifier", packageIdentifier);
		//auditedData.put("updateYaml", sanatizedUpdateYaml);

		this.auditRecordService.populateAndSaveAuditRecordUsingMapData(
				AuditOperationType.STREAM, AuditActionType.UPDATE,
				streamName, auditedData);
	}

	//@Before("execution(* org.springframework.cloud.dataflow.server.controller.AppRegistryController.registerAll(..))")
	//public void beforeClassicModeRegisterAllApps(JoinPoint joinPoint) {
	//	//Advice
	//	logger.info(" Aspect -> Register All APPS {}", joinPoint);
	//	AuditContext auditContext = auditContextHolder.getContext();
	//	auditContext.setSpanId(UUID.randomUUID().toString());
	//	auditContext.setData("Bulk Import: " + joinPoint.getArgs()[2]);
	//}
	//
	//
	//@After("execution(* org.springframework.cloud.dataflow.server.controller.AppRegistryController.registerAll(..))")
	//public void afterClassicModeRegisterAllApps(JoinPoint joinPoint) {
	//	//Advice
	//	logger.info(" Aspect -> Register All APPS {}", joinPoint);
	//
	//	String spanId = auditContextHolder.getContext().getSpanId();
	//	auditRecordService.populateAndSaveAuditRecord(
	//			AuditOperationType.APP_REGISTRATION, AuditActionType.CREATE, spanId,
	//			auditContextHolder.getContext().getData());
	//}
}
