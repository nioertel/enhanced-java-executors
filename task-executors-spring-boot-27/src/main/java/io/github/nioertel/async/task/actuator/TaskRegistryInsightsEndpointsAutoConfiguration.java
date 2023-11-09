package io.github.nioertel.async.task.actuator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;

@ConditionalOnWebApplication
@ConditionalOnClass({ TaskRegistryState.class, TaskRegistryMetrics.class })
public class TaskRegistryInsightsEndpointsAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskRegistryInsightsEndpointsAutoConfiguration.class);

	@ConditionalOnBean({ TaskRegistryInsightsRetriever.class })
	@Bean
	TaskRegistryMetricsEndpoint taskRegistryMetricsEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		LOGGER.info("Creating task registry metrics actuator endpoint based on {} insights retrievers.", registryInsightsRetrievers.size());
		return new TaskRegistryMetricsEndpoint(registryInsightsRetrievers);
	}

	@ConditionalOnBean({ TaskRegistryInsightsRetriever.class })
	@Bean
	TaskRegistryStateEndpoint taskRegistryStateEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		LOGGER.info("Creating task registry state actuator endpoint based on {} insights retrievers.", registryInsightsRetrievers.size());
		return new TaskRegistryStateEndpoint(registryInsightsRetrievers);
	}
}
