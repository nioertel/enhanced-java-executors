package io.github.nioertel.async.task.actuator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

@ConditionalOnWebApplication
@ConditionalOnClass({ TaskRegistryState.class, TaskRegistryMetrics.class })
public class TaskRegistryInsightsEndpointsAutoConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskRegistryInsightsEndpointsAutoConfiguration.class);

	private final List<TaskRegistryInsightsRetriever> registryInsightsRetrievers;

	private final MeterRegistry meterRegistry;

	private final List<ScheduledMetricsChangePublisher<?>> metricsChangePublishers = new ArrayList<>();

	public TaskRegistryInsightsEndpointsAutoConfiguration(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers,
			Optional<MeterRegistry> meterRegistry) {
		this.registryInsightsRetrievers = new ArrayList<>(registryInsightsRetrievers);
		this.meterRegistry = meterRegistry.orElse(null);
	}

	@ConditionalOnBean({ TaskRegistryInsightsRetriever.class })
	@Bean
	static TaskRegistryMetricsEndpoint taskRegistryMetricsEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		LOGGER.info("Creating task registry metrics actuator endpoint based on {} insights retrievers.", registryInsightsRetrievers.size());
		return new TaskRegistryMetricsEndpoint(registryInsightsRetrievers);
	}

	@ConditionalOnBean({ TaskRegistryInsightsRetriever.class })
	@Bean
	static TaskRegistryStateEndpoint taskRegistryStateEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		LOGGER.info("Creating task registry state actuator endpoint based on {} insights retrievers.", registryInsightsRetrievers.size());
		return new TaskRegistryStateEndpoint(registryInsightsRetrievers);
	}

	@PostConstruct
	void registerMicrometerIntegration() {
		if (null == meterRegistry) {
			LOGGER.debug("Skipping Micrometer registration because no bean of type {} was found.", MeterRegistry.class.getName());
			return;
		}
		registryInsightsRetrievers.forEach(registryInsightsRetriever -> {
			LOGGER.info("Registering Micrometer integration for task registry {} with change interval of {} ms (metrics prefix: task_registry_).",
					registryInsightsRetriever.getRegistryName(), registryInsightsRetriever.getMicrometerMetricsChangePublishingIntervalMillis());
			if (100 > registryInsightsRetriever.getMicrometerMetricsChangePublishingIntervalMillis()) {
				LOGGER.warn("Metrics publishing interval for task registry {} has been configured to be less than 100 ms. "
						+ "This may cause a performance impact on task execution.", registryInsightsRetriever.getRegistryName());
			} else {
			}
			metricsChangePublishers.add(new ScheduledMetricsChangePublisher<>(//
					new TaskRegistryMetricsPublisher(meterRegistry, registryInsightsRetriever.getRegistryName()), //
					registryInsightsRetriever::getMetricsSummary, //
					registryInsightsRetriever.getMicrometerMetricsChangePublishingIntervalMillis()//
			));
		});
	}

	@PreDestroy
	void shutdownMicrometerIntegration() {
		metricsChangePublishers.forEach(ScheduledMetricsChangePublisher::shutdown);
	}

	private static class ScheduledMetricsChangePublisher<T> {

		private final ScheduledExecutorService changePublisher;

		public ScheduledMetricsChangePublisher(Consumer<T> metricsPublisher, Supplier<T> metricsSupplier,
				int micrometerMetricsChangePublishingIntervalMillis) {
			this.changePublisher = Executors.newScheduledThreadPool(1);
			this.changePublisher.scheduleAtFixedRate(//
					() -> metricsPublisher.accept(metricsSupplier.get()), // command
					100, // initialDelay
					micrometerMetricsChangePublishingIntervalMillis, // period
					TimeUnit.MILLISECONDS// unit
			);
		}

		public void shutdown() {
			changePublisher.shutdownNow();
		}
	}

	private static class TaskRegistryMetricsPublisher implements Consumer<TaskRegistryMetricsSummary> {

		private final Tags tags;

		private final Timer exeuctorAssignmentWaitTime;

		private final Timer executionStartWaitTime;

		private final Timer executionTime;

		private TaskRegistryMetricsSummary lastInfo = new TaskRegistryMetricsSummary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);

		public TaskRegistryMetricsPublisher(MeterRegistry meterRegistry, String taskRegistryName) {
			this.tags = Tags.of("registryName", taskRegistryName);
			this.exeuctorAssignmentWaitTime = meterRegistry.timer("task_registry_executor_assignment_wait_time", tags);
			this.executionStartWaitTime = meterRegistry.timer("task_registry_execution_start_wait_time", tags);
			this.executionTime = meterRegistry.timer("task_registry_execution_time", tags);

			meterRegistry.gauge("task_registry_num_currently_submitted_tasks", tags, this, t -> t.lastInfo.getNumCurrentlySubmittedTasks());
			meterRegistry.gauge("task_registry_num_currently_executing_tasks", tags, this, t -> t.lastInfo.getNumCurrentlyExecutingTasks());
			meterRegistry.gauge("task_registry_num_currently_parked_tasks", tags, this, t -> t.lastInfo.getNumCurrentlyParkedTasks());
			meterRegistry.gauge("task_registry_total_num_submitted_tasks", tags, this, t -> t.lastInfo.getTotalNumSubmittedTasks());
			meterRegistry.gauge("task_registry_total_num_executed_tasks", tags, this, t -> t.lastInfo.getTotalNumExecutedTasks());
			meterRegistry.gauge("task_registry_total_num_discarded_tasks", tags, this, t -> t.lastInfo.getTotalNumDiscardedTasks());
		}

		public void accept(TaskRegistryMetricsSummary metrics) {
			publishMetrics(metrics);
		}

		public void publishMetrics(TaskRegistryMetricsSummary metrics) {
			this.exeuctorAssignmentWaitTime.record(metrics.getTotalExecutorAssignmentWaitTimeMs() - lastInfo.getTotalExecutorAssignmentWaitTimeMs(),
					TimeUnit.MILLISECONDS);
			this.executionStartWaitTime.record(metrics.getTotalWaitTimeForExecutionStartMs() - lastInfo.getTotalWaitTimeForExecutionStartMs(),
					TimeUnit.MILLISECONDS);
			this.executionTime.record(metrics.getTotalExecutionTimeMs() - lastInfo.getTotalExecutionTimeMs(), TimeUnit.MILLISECONDS);
			this.lastInfo = metrics;
		}
	}

}
