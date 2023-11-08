package io.github.nioertel.async.task.registry;

public interface TaskRegistryMetrics {

	long getStateVersion();

	/**
	 * @return Tasks which are pending for execution or are currently being executed.
	 */
	long getNumCurrentlySubmittedTasks();

	/**
	 * @return Tasks which are currently being executed.
	 */
	long getNumCurrentlyExecutingTasks();

	/**
	 * 
	 * @return Number of tasks that were parked during executor assignment and are still waiting for new assignment attempt.
	 */
	long getNumCurrentlyParkedTasks();

	long getTotalNumSubmittedTasks();

	long getTotalNumExecutedTasks();

	long getTotalNumDiscardedTasks();

	/**
	 * @return The total wait time for executor assignment.
	 */
	long getTotalExecutorAssignmentWaitTimeMs();

	/**
	 * @return The total time that was waited before task execution started in milliseconds.
	 */
	long getTotalWaitTimeForExecutionStartMs();

	/**
	 * @return The total execution time of all executed tasks in milliseconds.
	 */
	long getTotalExecutionTimeMs();
}
