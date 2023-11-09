package io.github.nioertel.async.task.executor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public interface RegistryBackedExecutorService extends ExecutorService {

	/**
	 * Override the default executor for state change listeners which runs them synchronously as part of the main
	 * operations. This may be used to move to an asynchronous processing of change listeners.
	 *
	 * @param executor
	 *            The executor.
	 */
	void setStateChangeListenerExecutor(Executor executor);

	/**
	 * Register a state change listener. Note that state change listeners are by default executed synchronously during
	 * operations and should therefore finish very fast.
	 * To override this default behaviour see {@link #setStateChangeListenerExecutor(Executor)}.
	 *
	 * @param stateChangeListener
	 *            The state change listener.
	 */
	void registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener);

	/**
	 * Override the default executor for metrics change listeners which runs them synchronously as part of the main
	 * operations. This may be used to move to an asynchronous processing of change listeners.
	 *
	 * @param executor
	 *            The executor.
	 */
	void setMetricsChangeListenerExecutor(Executor executor);

	/**
	 * Register a metrics change listener. Note that state change listeners are by default executed synchronously during
	 * operations and should therefore finish very fast.
	 * To override this default behaviour see {@link #setMetricsChangeListenerExecutor(Executor)}.
	 *
	 * @param stateChangeListener
	 *            The state change listener.
	 */
	void registerMetricsChangeListener(StateChangeListener<TaskRegistryMetrics> stateChangeListener);

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
