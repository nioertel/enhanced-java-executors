package io.github.nioertel.async.task.actuator;

import java.util.function.Supplier;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;

public class TaskRegistryInsightsRetriever {

	private final String registryName;

	private final Supplier<TaskRegistryState> stateSupplier;

	private final Supplier<TaskRegistryMetrics> metricsSupplier;

	private final int micrometerMetricsChangePublishingIntervalMillis;

	public TaskRegistryInsightsRetriever(String registryName, Supplier<TaskRegistryState> stateSupplier,
			Supplier<TaskRegistryMetrics> metricsSupplier, int micrometerMetricsChangePublishingIntervalMillis) {
		this.registryName = registryName;
		this.stateSupplier = stateSupplier;
		this.metricsSupplier = metricsSupplier;
		this.micrometerMetricsChangePublishingIntervalMillis = micrometerMetricsChangePublishingIntervalMillis;
	}

	public String getRegistryName() {
		return registryName;
	}

	public TaskRegistryMetricsSummary getMetricsSummary() {
		return new TaskRegistryMetricsSummary(metricsSupplier.get());
	}

	public TaskRegistryStateSummary getStateSummary() {
		return new TaskRegistryStateSummary(stateSupplier.get());
	}

	public int getMicrometerMetricsChangePublishingIntervalMillis() {
		return micrometerMetricsChangePublishingIntervalMillis;
	}

}