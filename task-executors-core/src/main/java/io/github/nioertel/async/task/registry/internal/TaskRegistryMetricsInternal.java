package io.github.nioertel.async.task.registry.internal;

import java.util.Locale;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;

class TaskRegistryMetricsInternal implements TaskRegistryMetrics {

	/**
	 * Tasks which are pending for execution or are currently being executed.
	 */
	long numCurrentlySubmittedTasks;

	/**
	 * Tasks which are currently being executed.
	 */
	long numCurrentlyExecutingTasks;

	/**
	 * Number of tasks that were parked during executor assignment and are still waiting for new assignment attempt.
	 */
	long numCurrentlyParkedTasks;

	long totalNumSubmittedTasks;

	long totalNumExecutedTasks;

	/**
	 * The total wait time for executor assignment.
	 */
	long totalExecutorAssignmentWaitTimeMs;

	/**
	 * The total time that was waited before task execution started in milliseconds.
	 */
	long totalWaitTimeForExecutionStartMs;

	/**
	 * The total execution time of all executed tasks in milliseconds.
	 */
	long totalExecutionTimeMs;

	/**
	 * Constructor.
	 */
	TaskRegistryMetricsInternal() {
	}

	/**
	 * Copy constructor.
	 *
	 * @param source
	 *            The source to copy from.
	 */
	TaskRegistryMetricsInternal(TaskRegistryMetricsInternal source) {
		this.numCurrentlySubmittedTasks = source.numCurrentlySubmittedTasks;
		this.numCurrentlyExecutingTasks = source.numCurrentlyExecutingTasks;
		this.numCurrentlyParkedTasks = source.numCurrentlyParkedTasks;
		this.totalNumSubmittedTasks = source.totalNumSubmittedTasks;
		this.totalNumExecutedTasks = source.totalNumExecutedTasks;
		this.totalExecutorAssignmentWaitTimeMs = source.totalExecutorAssignmentWaitTimeMs;
		this.totalWaitTimeForExecutionStartMs = source.totalWaitTimeForExecutionStartMs;
		this.totalExecutionTimeMs = source.totalExecutionTimeMs;
	}

	@Override
	public long getNumCurrentlySubmittedTasks() {
		return numCurrentlySubmittedTasks;
	}

	@Override
	public long getNumCurrentlyExecutingTasks() {
		return numCurrentlyExecutingTasks;
	}

	@Override
	public long getNumCurrentlyParkedTasks() {
		return numCurrentlyParkedTasks;
	}

	@Override
	public long getTotalNumSubmittedTasks() {
		return totalNumSubmittedTasks;
	}

	@Override
	public long getTotalNumExecutedTasks() {
		return totalNumExecutedTasks;
	}

	@Override
	public long getTotalExecutorAssignmentWaitTimeMs() {
		return totalExecutorAssignmentWaitTimeMs;
	}

	@Override
	public long getTotalWaitTimeForExecutionStartMs() {
		return totalWaitTimeForExecutionStartMs;
	}

	@Override
	public long getTotalExecutionTimeMs() {
		return totalExecutionTimeMs;
	}

	@Override
	public String toString() {
		return new StringBuilder()//
				.append("TaskRegistryMetrics: ").append(System.lineSeparator())//
				.append("  gauge(num-currently-submitted-tasks):    ").append(String.format(Locale.US, "%,d", numCurrentlySubmittedTasks))
				.append(System.lineSeparator())//
				.append("  gauge(num-currently-executing-tasks):    ").append(String.format(Locale.US, "%,d", numCurrentlyExecutingTasks))
				.append(System.lineSeparator())//
				.append("  gauge(num-currently-parked-tasks):       ").append(String.format(Locale.US, "%,d", numCurrentlyParkedTasks))
				.append(System.lineSeparator())//
				.append("  gauge(total-num-submitted-tasks):        ").append(String.format(Locale.US, "%,d", totalNumSubmittedTasks))
				.append(System.lineSeparator())//
				.append("  gauge(total-num-executed-tasks):         ").append(String.format(Locale.US, "%,d", totalNumExecutedTasks))
				.append(System.lineSeparator())//
				.append("    sum(executor-assignment-wait-time-ms): ").append(String.format(Locale.US, "%,d", totalExecutorAssignmentWaitTimeMs))
				.append(System.lineSeparator())//
				.append("    sum(execution-start-wait-time-ms):     ").append(String.format(Locale.US, "%,d", totalWaitTimeForExecutionStartMs))
				.append(System.lineSeparator())//
				.append("    sum(execution-time-ms):                ").append(String.format(Locale.US, "%,d", totalExecutionTimeMs))//
				.toString();
	}

}
