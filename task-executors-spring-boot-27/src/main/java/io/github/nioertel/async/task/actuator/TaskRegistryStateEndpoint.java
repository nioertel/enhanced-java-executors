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

@Endpoint(id = "taskRegistryState")
public class TaskRegistryStateEndpoint {

	private final Map<String, TaskRegistryInsightsRetriever> registryInsightsRetrievers;

	TaskRegistryStateEndpoint(List<TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
		this.registryInsightsRetrievers = registryInsightsRetrievers.stream().collect(Collectors.toMap(//
				m -> m.getRegistryName(), //
				Function.identity()//
		));
	}

	@ReadOperation
	public TaskRegistryStateSummaryOverall state() {
		return TaskRegistryStateSummaryOverall.fromProviderByRegistry(registryInsightsRetrievers);
	}

	public static class TaskRegistryStateSummaryOverall {

		private final LocalDateTime snapshotTime;

		private final Map<String, TaskRegistryStateSummary> stateByTaskRegistry;

		@JsonCreator
		private TaskRegistryStateSummaryOverall(//
				@JsonProperty("snapshotTime") LocalDateTime snapshotTime, //
				@JsonProperty("stateByTaskRegistry") Map<String, TaskRegistryStateSummary> stateByTaskRegistry) {
			this.snapshotTime = snapshotTime;
			this.stateByTaskRegistry = new LinkedHashMap<>(stateByTaskRegistry);
		}

		public static TaskRegistryStateSummaryOverall fromProviderByRegistry(Map<String, TaskRegistryInsightsRetriever> registryInsightsRetrievers) {
			Map<String, TaskRegistryStateSummary> stateByTaskRegistry = new LinkedHashMap<>();
			registryInsightsRetrievers.forEach((registryName, insightsRetriever) -> {
				stateByTaskRegistry.put(registryName, insightsRetriever.getStateSummary());
			});
			return new TaskRegistryStateSummaryOverall(//
					LocalDateTime.now(ZoneId.of("Europe/Berlin")), //
					stateByTaskRegistry//
			);
		}

		public LocalDateTime getSnapshotTime() {
			return snapshotTime;
		}

		public Map<String, TaskRegistryStateSummary> getStateByTaskRegistry() {
			return Collections.unmodifiableMap(stateByTaskRegistry);
		}

	}

}
