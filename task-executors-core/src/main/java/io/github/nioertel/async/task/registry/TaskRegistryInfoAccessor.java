package io.github.nioertel.async.task.registry;

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

}