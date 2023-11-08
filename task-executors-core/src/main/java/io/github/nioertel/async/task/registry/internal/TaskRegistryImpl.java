package io.github.nioertel.async.task.registry.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment.ExecutorIdAssignmentCommand;
import io.github.nioertel.async.task.registry.Identifiable;
import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.state.StateAccessor;
import io.github.nioertel.async.task.registry.state.StateChangeListener;
import io.github.nioertel.async.task.registry.state.StateChangeLstenerNotifier;

final class TaskRegistryImpl implements TaskRegistry {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskRegistryImpl.class);

	private final ThreadTrackingTaskDecoratorImpl threadTrackingTaskDecorator = new ThreadTrackingTaskDecoratorImpl(this);

	/**
	 * Function that assigns an executor ID for the given task during submission.
	 */
	private final ExecutorIdAssigner executorIdAssigner;

	private final StateAccessor<TaskRegistryStateInternal, TaskRegistryState> stateAccessor =
			new StateAccessor<>(TaskRegistryStateInternal::new, TaskRegistryStateInternal::new);

	private final StateAccessor<TaskRegistryMetricsInternal, TaskRegistryMetrics> metricsAccessor =
			new StateAccessor<>(TaskRegistryMetricsInternal::new, TaskRegistryMetricsInternal::new);

	private final StateChangeLstenerNotifier<TaskRegistryState> stateChangeListenerNotifier = new StateChangeLstenerNotifier<>();

	private final StateChangeLstenerNotifier<TaskRegistryMetrics> metricsChangeListenerNotifier = new StateChangeLstenerNotifier<>();

	public TaskRegistryImpl(ExecutorIdAssigner executorIdAssigner) {
		this.executorIdAssigner = executorIdAssigner;
	}

	@Override
	public ThreadTrackingTaskDecorator getTrackingTaskDecorator() {
		return threadTrackingTaskDecorator;
	}

	@Override
	public long generateNewTaskId() {
		return stateAccessor.update(//
				"generateNewTaskId", //
				state -> {
					return state.taskIdProvider.incrementAndGet();
				}, //
				stateChangeListenerNotifier.getListenerWrapper());
	}

	@Override
	public void taskExecutionStarted(Identifiable task, Thread assignedThread) {
		long taskId = task.getId();
		long assignedThreadId = assignedThread.getId();
		long executionStartDateEpochMillis = System.currentTimeMillis();

		TaskState taskState = stateAccessor.update(//
				"taskExecutionStarted", //
				state -> {
					TaskStateInternal taskStateInternal = state.currentlySubmittedTasks.get(taskId);
					taskStateInternal.setExecutionStartDateEpochMillis(executionStartDateEpochMillis);
					taskStateInternal.setAssignedThreadId(assignedThreadId);
					taskStateInternal.setTaskProgress(TaskProgress.RUNNING);
					state.currentlyExecutingTasks.add(taskId);
					state.threadIdTaskIdMappings.put(assignedThreadId, taskId);
					return new TaskStateInternal(taskStateInternal);
				}, //
				stateChangeListenerNotifier.getListenerWrapper());
		metricsAccessor.updateWithoutResult(//
				"taskSubmitted", //
				state -> {
					state.numCurrentlyExecutingTasks++;
					state.totalWaitTimeForExecutionStartMs += (executionStartDateEpochMillis - taskState.getSubmissionDateEpochMillis());
				}, //
				metricsChangeListenerNotifier.getListenerWrapper()//
		);
	}

	@Override
	public void taskExecutionFinished(Identifiable task) {
		long taskId = task.getId();
		long executionEndDateEpochMillis = System.currentTimeMillis();

		TaskState taskState = stateAccessor.update(//
				"taskExecutionFinished", //
				state -> {
					state.currentlyExecutingTasks.remove(taskId);
					// once execution finishes we can remove the task from the registry
					TaskStateInternal taskStateInternal = state.currentlySubmittedTasks.remove(taskId);
					taskStateInternal.setExecutionEndDateEpochMillis(executionEndDateEpochMillis);
					taskStateInternal.setTaskProgress(TaskProgress.FINISHED);
					state.threadIdTaskIdMappings.remove(taskStateInternal.getAssignedThreadId());

					// remove from submitted tasks by family+executor tracker
					removeTaskFromExecutorAndFamilyAssignmentTracking(state, taskStateInternal);
					return new TaskStateInternal(taskStateInternal);
				}, //
				stateChangeListenerNotifier.getListenerWrapper());
		metricsAccessor.updateWithoutResult(//
				"taskSubmitted", //
				state -> {
					state.numCurrentlySubmittedTasks--;
					state.numCurrentlyExecutingTasks--;
					state.totalNumExecutedTasks++;
					state.totalExecutionTimeMs += (executionEndDateEpochMillis - taskState.getExecutionStartDateEpochMillis());
				}, //
				metricsChangeListenerNotifier.getListenerWrapper()//
		);
	}

	@Override
	public List<TaskState> resubmitParkedTasks() {
		List<TaskState> successfullyResubmittedTasks = stateAccessor.update(//
				"resubmitParkedTasks", //
				state -> {
					List<TaskState> resubmittedTasks = new ArrayList<>();
					for (long taskId : state.currentlyParkedTasks) {
						TaskStateInternal taskState = state.currentlySubmittedTasks.get(taskId);
						ExecutorIdAssignment executorIdAssignment = executorIdAssigner.assignExecutorId(this, taskState);
						if (ExecutorIdAssignmentCommand.ASSIGN == executorIdAssignment.getCommand()) {
							taskState.setTaskProgress(TaskProgress.SUBMITTED);
							taskState.setAssignedExecutorId(executorIdAssignment.getAssignedExecutorId());
							taskState.setExecutorAssignedDateEpochMillis(System.currentTimeMillis());
							state.currentlyAssignedTasksByExecutorAndTaskFamily//
									.computeIfAbsent(taskState.getAssignedExecutorId(), e -> new LinkedHashMap<>())//
									.computeIfAbsent(taskState.getTaskFamilyId(), f -> new LinkedHashSet<>())//
									.add(taskId);
							resubmittedTasks.add(new TaskStateInternal(taskState));
						} else {
							long currentWaitingTimeMs = System.currentTimeMillis() - taskState.getSubmissionDateEpochMillis();
							if (TaskProgress.PARKED == taskState.getTaskProgress() && currentWaitingTimeMs > 30_000L) {
								// move into state LONG_PARKED
								taskState.setTaskProgress(TaskProgress.LONG_PARKED);
								LOGGER.warn("Task {} has been parked for {} seconds and has still not been assigned for execution!",
										taskState.getId(), currentWaitingTimeMs / 1_000L);
							}
						}
					}
					for (TaskState taskState : resubmittedTasks) {
						state.currentlyParkedTasks.remove(taskState.getId());
					}
					return resubmittedTasks;
				}, //
				stateChangeListenerNotifier.getListenerWrapper()//
		);
		metricsAccessor.updateWithoutResult(//
				"taskSubmitted", //
				metricsState -> {
					for (TaskState taskState : successfullyResubmittedTasks) {
						metricsState.totalExecutorAssignmentWaitTimeMs +=
								(taskState.getExecutorAssignedDateEpochMillis() - taskState.getSubmissionDateEpochMillis());
						metricsState.numCurrentlyParkedTasks--;
					}
				}, //
				metricsChangeListenerNotifier.getListenerWrapper()//
		);
		return successfullyResubmittedTasks;
	}

	@Override
	public TaskStateInternal taskSubmitted(Identifiable task, Thread submittingThread) {
		return submitTask("taskSubmitted", submittingThread.getId(), task.getId());
	}

	private void removeTaskFromExecutorAndFamilyAssignmentTracking(TaskRegistryStateInternal state, TaskState taskState) {
		Map<Long, Set<Long>> currentlyExecutingTasksByTaskFamilyForExecutor =
				state.currentlyAssignedTasksByExecutorAndTaskFamily.get(taskState.getAssignedExecutorId());
		if (null == currentlyExecutingTasksByTaskFamilyForExecutor) {
			return;
		}
		Set<Long> tasksForParentTaskFamily = currentlyExecutingTasksByTaskFamilyForExecutor.get(taskState.getTaskFamilyId());
		if (null == tasksForParentTaskFamily) {
			return;
		}
		tasksForParentTaskFamily.remove(taskState.getId());
		if (tasksForParentTaskFamily.isEmpty()) {
			currentlyExecutingTasksByTaskFamilyForExecutor.remove(taskState.getTaskFamilyId());
			if (currentlyExecutingTasksByTaskFamilyForExecutor.isEmpty()) {
				state.currentlyAssignedTasksByExecutorAndTaskFamily.remove(taskState.getAssignedExecutorId());
			}
		}
	}

	@Override
	public TaskState taskDiscarded(long taskId) {
		long executionEndDateEpochMillis = System.currentTimeMillis();
		TaskState taskState = stateAccessor.update(//
				"taskDiscarded", //
				state -> {
					// once execution finishes we can remove the task from the registry
					TaskStateInternal taskStateInternal = state.currentlySubmittedTasks.remove(taskId);
					if (null == taskStateInternal) {
						// Task does not exist / nothing to do
						return null;
					}
					taskStateInternal.setExecutionEndDateEpochMillis(executionEndDateEpochMillis);
					if (taskStateInternal.getTaskProgress() == TaskProgress.RUNNING) {
						state.currentlyExecutingTasks.remove(taskId);
						taskStateInternal.setTaskProgress(TaskProgress.DISCARDED_WHILE_RUNNING);
					} else if (state.currentlyParkedTasks.remove(taskId)) {
						taskStateInternal.setTaskProgress(TaskProgress.DISCARDED_WHILE_PARKED);
					} else {
						taskStateInternal.setTaskProgress(TaskProgress.DISCARDED);
					}

					state.threadIdTaskIdMappings.remove(taskStateInternal.getAssignedThreadId());

					// remove from submitted tasks by family+executor tracker
					removeTaskFromExecutorAndFamilyAssignmentTracking(state, taskStateInternal);
					return new TaskStateInternal(taskStateInternal);
				}, //
				stateChangeListenerNotifier.getListenerWrapper());
		if (null != taskState) {
			metricsAccessor.updateWithoutResult(//
					"taskDiscarded", //
					state -> {
						state.numCurrentlySubmittedTasks--;
						state.totalNumDiscardedTasks++;
						if (TaskProgress.DISCARDED_WHILE_RUNNING == taskState.getTaskProgress()) {
							state.numCurrentlyExecutingTasks--;
						} else if (TaskProgress.DISCARDED_WHILE_PARKED == taskState.getTaskProgress()) {
							state.numCurrentlyParkedTasks--;
						}
					}, //
					metricsChangeListenerNotifier.getListenerWrapper()//
			);
		}
		return taskState;
	}

	private TaskStateInternal submitTask(String originalOperationName, long submittingThreadId, long taskId) {
		long submissionStartDateMs = System.currentTimeMillis();
		TaskStateInternal taskState = stateAccessor.update(//
				originalOperationName, //
				state -> {
					Long parentTaskId = state.threadIdTaskIdMappings.get(submittingThreadId);
					TaskStateInternal newTaskState;
					if (null != parentTaskId) {
						// the current thread is running within the executor and the new task will too
						TaskStateInternal parentTaskState = state.currentlySubmittedTasks.get(parentTaskId);
						newTaskState = new TaskStateInternal(//
								taskId // id
								, parentTaskState // parentTaskState
								, parentTaskState.getTaskFamilyId() // taskFamilyId
								, parentTaskState.getStackLevel() + 1L // taskStackLevel
								, submissionStartDateMs // submissionDateEpochMillis
						);
					} else {
						// the new task is independent / i.e. a new top level task
						newTaskState = new TaskStateInternal(//
								taskId // id
								, null // parentTaskState
								, state.taskFamilyIdProvider.incrementAndGet() // taskFamilyId
								, 0L // taskStackLevel
								, submissionStartDateMs // submissionDateEpochMillis
						);
					}
					ExecutorIdAssignment executorIdAssignment = executorIdAssigner.assignExecutorId(this, newTaskState);
					if (ExecutorIdAssignmentCommand.ASSIGN == executorIdAssignment.getCommand()) {
						newTaskState.setTaskProgress(TaskProgress.SUBMITTED);
						newTaskState.setAssignedExecutorId(executorIdAssignment.getAssignedExecutorId());
						newTaskState.setExecutorAssignedDateEpochMillis(System.currentTimeMillis());
						state.currentlyAssignedTasksByExecutorAndTaskFamily//
								.computeIfAbsent(newTaskState.getAssignedExecutorId(), e -> new LinkedHashMap<>())//
								.computeIfAbsent(newTaskState.getTaskFamilyId(), f -> new LinkedHashSet<>())//
								.add(taskId);
					} else {
						newTaskState.setTaskProgress(TaskProgress.PARKED);
						state.currentlyParkedTasks.add(taskId);
					}
					state.currentlySubmittedTasks.put(taskId, newTaskState);

					return new TaskStateInternal(newTaskState);
				}, //
				stateChangeListenerNotifier.getListenerWrapper());
		metricsAccessor.updateWithoutResult(//
				"taskSubmitted", //
				metricsState -> {
					metricsState.numCurrentlySubmittedTasks++;
					metricsState.totalNumSubmittedTasks++;
					if (TaskProgress.PARKED == taskState.getTaskProgress()) {
						metricsState.numCurrentlyParkedTasks++;
					} else {
						metricsState.totalExecutorAssignmentWaitTimeMs += (taskState.getExecutorAssignedDateEpochMillis() - submissionStartDateMs);
					}
				}, //
				metricsChangeListenerNotifier.getListenerWrapper()//
		);
		return taskState;
	}

	@Override
	public TaskState getTaskStateSnapshot(long taskId) {
		return stateAccessor.extract(registryState -> {
			return new TaskStateInternal(registryState.currentlySubmittedTasks.get(taskId));
		});
	}

	@Override
	public void setStateChangeListenerExecutor(Executor executor) {
		stateChangeListenerNotifier.setStateChangeListenerExecutor(executor);
	}

	@Override
	public void registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener) {
		stateChangeListenerNotifier.registerStateChangeListener(stateChangeListener);
	}

	@Override
	public void setMetricsChangeListenerExecutor(Executor executor) {
		metricsChangeListenerNotifier.setStateChangeListenerExecutor(executor);
	}

	@Override
	public void registerMetricsChangeListener(StateChangeListener<TaskRegistryMetrics> stateChangeListener) {
		metricsChangeListenerNotifier.registerStateChangeListener(stateChangeListener);
	}

	@Override
	public TaskRegistryMetrics getMetricsSnapshot() {
		return metricsAccessor.snapshot();
	}

	@Override
	public TaskRegistryState getStateSnapshot() {
		return stateAccessor.snapshot();
	}

}