package io.github.nioertel.async.task.registry;

import java.util.Set;

public interface TaskRegistryInfoAccessor {

	/**
	 * Get a snapshot of the current metrics.
	 *
	 * @return The metrics snapshot.
	 */
	TaskRegistryMetrics getMetricsSnapshot();

	/**
	 * Get a snapshot of the current state.
	 *
	 * @return The state snapshot.
	 */
	TaskRegistryState getStateSnapshot();

	default Set<Long> getCurrentlyExecutingTasksSnapshot() {
		return getStateSnapshot().getCurrentlyExecutingTasks();
	}
}