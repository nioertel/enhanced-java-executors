package io.github.nioertel.async.task.registry.internal;

import java.util.List;
import java.util.concurrent.Executor;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.Identifiable;
import io.github.nioertel.async.task.registry.TaskRegistryInfoAccessor;
import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public interface TaskRegistry extends TaskRegistryInfoAccessor {

	/**
	 * Creator for task registries.
	 *
	 * @param executorIdAssigner
	 *            Function that assigns an executor ID for the given task during submission.
	 *
	 * @return The task registry.
	 */
	static TaskRegistry newTaskRegistry(ExecutorIdAssigner executorIdAssigner) {
		return new TaskRegistryImpl(executorIdAssigner);
	}

	/**
	 * @return A task decorator that adds tracking integration for this task registry. Note that this decorator should
	 *         always be the out-most wrapper for the task.
	 */
	ThreadTrackingTaskDecorator getTrackingTaskDecorator();

	/**
	 * @return A new task id that has not been provided before.
	 */
	long generateNewTaskId();

	/**
	 * This method should be called as soon as a task enters state running.
	 *
	 * @param task
	 *            The task.
	 * @param assignedThread
	 *            The assigned thread that executes the task.
	 */
	void taskExecutionStarted(Identifiable task, Thread assignedThread);

	/**
	 * This method should be called once a task leaves state running.
	 *
	 * @param task
	 *            The task.
	 */
	void taskExecutionFinished(Identifiable task);

	/**
	 * Resubmit all tasks which are in state parked.
	 *
	 * @return The resubmission results (may be empty if no resubmissions are necessary).
	 */
	List<TaskState> resubmitParkedTasks();

	/**
	 * Discard the given task from the registry.
	 *
	 * @param taskId
	 *            The task ID.
	 *
	 * @return The task state.
	 */
	TaskState taskDiscarded(long taskId);

	/**
	 * Get a snapshot of the current state
	 *
	 * @param taskId
	 *            The task ID.
	 *
	 * @return A snapshot of the task state.
	 */
	TaskState getTaskStateSnapshot(long taskId);

	/**
	 * Register a task with its submitting thread.
	 *
	 * @param task
	 *            The task.
	 * @param submittingThread
	 *            The submitting thread id.
	 *
	 * @return The state info object that provides status insights on the task. This object reflects a live view on the
	 *         task's state within the registry.
	 */
	TaskState taskSubmitted(Identifiable task, Thread submittingThread);

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

}