package io.github.nioertel.async.task.actuator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;

public class TaskRegistryMetricsSummary {

	private final long stateVersion;

	/**
	 * Tasks which are pending for execution or are currently being executed.
	 */
	private final long numCurrentlySubmittedTasks;

	/**
	 * Tasks which are currently being executed.
	 */
	private final long numCurrentlyExecutingTasks;

	/**
	 * Number of tasks that were parked during executor assignment and are still waiting for new assignment attempt.
	 */
	private final long numCurrentlyParkedTasks;

	private final long totalNumSubmittedTasks;

	private final long totalNumExecutedTasks;

	private final long totalNumDiscardedTasks;

	/**
	 * The total wait time for executor assignment.
	 */
	private final long totalExecutorAssignmentWaitTimeMs;

	/**
	 * The total time that was waited before task execution started in milliseconds.
	 */
	private final long totalWaitTimeForExecutionStartMs;

	/**
	 * The total execution time of all executed tasks in milliseconds.
	 */
	private final long totalExecutionTimeMs;

	@JsonCreator
	public TaskRegistryMetricsSummary(//
			@JsonProperty("stateVersion") long stateVersion, //
			@JsonProperty("numCurrentlySubmittedTasks") long numCurrentlySubmittedTasks, //
			@JsonProperty("numCurrentlyExecutingTasks") long numCurrentlyExecutingTasks, //
			@JsonProperty("numCurrentlyParkedTasks") long numCurrentlyParkedTasks, //
			@JsonProperty("totalNumSubmittedTasks") long totalNumSubmittedTasks, //
			@JsonProperty("totalNumExecutedTasks") long totalNumExecutedTasks, //
			@JsonProperty("totalNumDiscardedTasks") long totalNumDiscardedTasks, //
			@JsonProperty("totalExecutorAssignmentWaitTimeMs") long totalExecutorAssignmentWaitTimeMs, //
			@JsonProperty("totalWaitTimeForExecutionStartMs") long totalWaitTimeForExecutionStartMs, //
			@JsonProperty("totalExecutionTimeMs") long totalExecutionTimeMs//
	) {
		this.stateVersion = stateVersion;
		this.numCurrentlySubmittedTasks = numCurrentlySubmittedTasks;
		this.numCurrentlyExecutingTasks = numCurrentlyExecutingTasks;
		this.numCurrentlyParkedTasks = numCurrentlyParkedTasks;
		this.totalNumSubmittedTasks = totalNumSubmittedTasks;
		this.totalNumExecutedTasks = totalNumExecutedTasks;
		this.totalNumDiscardedTasks = totalNumDiscardedTasks;
		this.totalExecutorAssignmentWaitTimeMs = totalExecutorAssignmentWaitTimeMs;
		this.totalWaitTimeForExecutionStartMs = totalWaitTimeForExecutionStartMs;
		this.totalExecutionTimeMs = totalExecutionTimeMs;
	}

	public TaskRegistryMetricsSummary(TaskRegistryMetrics metrics) {
		this.stateVersion = metrics.getStateVersion();
		this.numCurrentlySubmittedTasks = metrics.getNumCurrentlySubmittedTasks();
		this.numCurrentlyExecutingTasks = metrics.getNumCurrentlyExecutingTasks();
		this.numCurrentlyParkedTasks = metrics.getNumCurrentlyParkedTasks();
		this.totalNumSubmittedTasks = metrics.getTotalNumSubmittedTasks();
		this.totalNumExecutedTasks = metrics.getTotalNumExecutedTasks();
		this.totalNumDiscardedTasks = metrics.getTotalNumDiscardedTasks();
		this.totalExecutorAssignmentWaitTimeMs = metrics.getTotalExecutorAssignmentWaitTimeMs();
		this.totalWaitTimeForExecutionStartMs = metrics.getTotalWaitTimeForExecutionStartMs();
		this.totalExecutionTimeMs = metrics.getTotalExecutionTimeMs();
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public long getNumCurrentlySubmittedTasks() {
		return numCurrentlySubmittedTasks;
	}

	public long getNumCurrentlyExecutingTasks() {
		return numCurrentlyExecutingTasks;
	}

	public long getNumCurrentlyParkedTasks() {
		return numCurrentlyParkedTasks;
	}

	public long getTotalNumSubmittedTasks() {
		return totalNumSubmittedTasks;
	}

	public long getTotalNumExecutedTasks() {
		return totalNumExecutedTasks;
	}

	public long getTotalNumDiscardedTasks() {
		return totalNumDiscardedTasks;
	}

	public long getTotalExecutorAssignmentWaitTimeMs() {
		return totalExecutorAssignmentWaitTimeMs;
	}

	public long getTotalWaitTimeForExecutionStartMs() {
		return totalWaitTimeForExecutionStartMs;
	}

	public long getTotalExecutionTimeMs() {
		return totalExecutionTimeMs;
	}

}
