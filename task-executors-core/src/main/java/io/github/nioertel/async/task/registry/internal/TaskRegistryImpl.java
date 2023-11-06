package io.github.nioertel.async.task.registry.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment;
import io.github.nioertel.async.task.registry.Identifiable;
import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment.ExecutorIdAssignmentCommand;
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

	private final StateChangeLstenerNotifier<TaskRegistryState> listenerNotifier = new StateChangeLstenerNotifier<>();

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
				state -> state.taskIdProvider.incrementAndGet(), //
				listenerNotifier.getListenerWrapper());
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
					state.currentlyExecutingTasks.add(taskId);
					state.threadIdTaskIdMappings.put(assignedThreadId, taskId);
					state.currentlyExecutingTasksByTaskFamily.computeIfAbsent(taskStateInternal.getTaskFamilyId(), f -> new HashSet<>()).add(taskId);
					return taskStateInternal;
				}, //
				listenerNotifier.getListenerWrapper());
		metricsAccessor.update(//
				"taskSubmitted", //
				state -> {
					state.numCurrentlyExecutingTasks++;
					state.totalWaitTimeForExecutionStartMs += (executionStartDateEpochMillis - taskState.getSubmissionDateEpochMillis());
					return null;
				}, //
				null//
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
					taskStateInternal.setAssignedExecutorId(executionEndDateEpochMillis);
					state.threadIdTaskIdMappings.remove(taskStateInternal.getAssignedThreadId());
					Set<Long> tasksForParentTaskFamily = state.currentlyExecutingTasksByTaskFamily.get(taskStateInternal.getTaskFamilyId());
					tasksForParentTaskFamily.remove(taskId);
					if (tasksForParentTaskFamily.isEmpty()) {
						state.currentlyExecutingTasksByTaskFamily.remove(taskStateInternal.getTaskFamilyId());
					}
					return taskStateInternal;
				}, //
				listenerNotifier.getListenerWrapper());
		metricsAccessor.update(//
				"taskSubmitted", //
				state -> {
					state.numCurrentlySubmittedTasks--;
					state.numCurrentlyExecutingTasks--;
					state.totalNumExecutedTasks++;
					state.totalExecutionTimeMs += (executionEndDateEpochMillis - taskState.getExecutionStartDateEpochMillis());
					return null;
				}, //
				null//
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
							taskState.setExecutorAssignmentState(TaskExecutorAssignmentState.ASSIGNED);
							taskState.setAssignedExecutorId(executorIdAssignment.getAssignedExecutorId());
							taskState.setExecutorAssignedDateEpochMillis(System.currentTimeMillis());
							resubmittedTasks.add(taskState);
						} else {
							long currentWaitingTimeMs = System.currentTimeMillis() - taskState.getSubmissionDateEpochMillis();
							if (currentWaitingTimeMs > 30_000L) {
								// TODO: Work around log flood!!!
								LOGGER.warn("Task {} has been parked for {} seconds and is still not ready for execution!", taskState.getId(),
										currentWaitingTimeMs / 1_000L);
							}
						}
					}
					return resubmittedTasks;
				}, //
				listenerNotifier.getListenerWrapper()//
		);
		metricsAccessor.update(//
				"taskSubmitted", //
				metricsState -> {
					for (TaskState taskState : successfullyResubmittedTasks) {
						metricsState.totalExecutorAssignmentWaitTimeMs +=
								(taskState.getExecutorAssignedDateEpochMillis() - taskState.getSubmissionDateEpochMillis());
						metricsState.numCurrentlyParkedTasks--;
					}
					return null;
				}, //
				null//
		);
		return successfullyResubmittedTasks;
	}

	@Override
	public TaskStateInternal taskSubmitted(Identifiable task, Thread submittingThread) {
		return submitTask(submittingThread.getId(), task.getId());
	}

	private TaskStateInternal submitTask(long submittingThreadId, long taskId) {
		long submissionStartDateMs = System.currentTimeMillis();
		TaskStateInternal taskState = stateAccessor.update(//
				"taskSubmitted", //
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
						newTaskState.setExecutorAssignmentState(TaskExecutorAssignmentState.ASSIGNED);
						newTaskState.setAssignedExecutorId(executorIdAssignment.getAssignedExecutorId());
						newTaskState.setExecutorAssignedDateEpochMillis(System.currentTimeMillis());
					} else {
						newTaskState.setExecutorAssignmentState(TaskExecutorAssignmentState.PARKED);
						state.currentlyParkedTasks.add(taskId);
					}
					state.currentlySubmittedTasks.put(taskId, newTaskState);

					return newTaskState;
				}, //
				listenerNotifier.getListenerWrapper());
		metricsAccessor.update(//
				"taskSubmitted", //
				metricsState -> {
					metricsState.numCurrentlySubmittedTasks++;
					metricsState.totalNumSubmittedTasks++;
					metricsState.totalExecutorAssignmentWaitTimeMs += (taskState.getExecutorAssignedDateEpochMillis() - submissionStartDateMs);
					if (TaskExecutorAssignmentState.PARKED == taskState.getExecutorAssignmentState()) {
						metricsState.numCurrentlyParkedTasks++;
					}
					return null;
				}, //
				null//
		);
		return taskState;
	}

	@Override
	public void setStateChangeListenerExecutor(Executor executor) {
		listenerNotifier.setStateChangeListenerExecutor(executor);
	}

	@Override
	public void registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener) {
		listenerNotifier.registerStateChangeListener(stateChangeListener);
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