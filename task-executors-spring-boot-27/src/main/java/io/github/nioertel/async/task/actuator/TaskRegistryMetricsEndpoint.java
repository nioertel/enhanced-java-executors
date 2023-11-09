package io.github.nioertel.async.task.actuator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Endpoint(id = "taskRegistryMetrics")
public class TaskRegistryMetricsEndpoint {

	private final Map<String, TaskRegistryInsightsRetriever> registryInsightsRetrievers;

	TaskRegistryMetricsEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		this.registryInsightsRetrievers = registryInsightsRetrievers.stream().collect(Collectors.toMap(//
				m -> m.getRegistryName(), //
				Function.identity()//
		));
	}

	@ReadOperation
	public TaskRegistryMetricsSummaryOverall metrics() {
		return TaskRegistryMetricsSummaryOverall.fromProviderByRegistry(registryInsightsRetrievers);
	}

	public static class TaskRegistryMetricsSummaryOverall {

		private final LocalDateTime snapshotTime;

		private final Map<String, TaskRegistryMetricsSummary> metricsByTaskRegistry;

		@JsonCreator
		private TaskRegistryMetricsSummaryOverall(//
				@JsonProperty("snapshotTime") LocalDateTime snapshotTime, //
				@JsonProperty("metricsByTaskRegistry") Map<String, TaskRegistryMetricsSummary> metricsByTaskRegistry) {
			this.snapshotTime = snapshotTime;
			this.metricsByTaskRegistry = new LinkedHashMap<>(metricsByTaskRegistry);
		}

		public static TaskRegistryMetricsSummaryOverall fromProviderByRegistry(
				Map<String, TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
			Map<String, TaskRegistryMetricsSummary> metricsByTaskRegistry = new LinkedHashMap<>();
			registryInsightsRetrievers.forEach((registryName, insightsRetriever) -> {
				metricsByTaskRegistry.put(registryName, insightsRetriever.getMetricsSummary());
			});
			return new TaskRegistryMetricsSummaryOverall(//
					LocalDateTime.now(ZoneId.of("Europe/Berlin")), //
					metricsByTaskRegistry//
			);
		}

		public LocalDateTime getSnapshotTime() {
			return snapshotTime;
		}

		public Map<String, TaskRegistryMetricsSummary> getMetricsByTaskRegistry() {
			return Collections.unmodifiableMap(metricsByTaskRegistry);
		}

	}

}
