package io.github.nioertel.async.task.registry.internal;

import java.util.Locale;

import io.github.nioertel.async.task.registry.TaskState;

class TaskStateInternal implements TaskState {

	private final long id;

	private final TaskStateInternal parentTaskState;

	private final long taskFamilyId;

	/**
	 * The stack level of the task (i.e. the depth in the call stack for this execution (starting with 0 for tasks, that are
	 * initially submitted to this executor, increasing by one for a task that is submitted from within another task that is
	 * being executed by this executor.
	 */
	private final long stackLevel;

	private final long submissionDateEpochMillis;

	private TaskExecutorAssignmentState executorAssignmentState = TaskExecutorAssignmentState.PENDING;

	private long assignedExecutorId = -1;

	private long assignedThreadId = -1;

	private long executorAssignedDateEpochMillis = -1;

	private long executionStartDateEpochMillis = -1;

	private long executionEndDateEpochMillis = -1;

	TaskStateInternal(long id, TaskStateInternal parentTaskState, long taskFamilyId, long stackLevel, long submissionDateEpochMillis) {
		this.id = id;
		this.parentTaskState = parentTaskState;
		this.taskFamilyId = taskFamilyId;
		this.stackLevel = stackLevel;
		this.submissionDateEpochMillis = submissionDateEpochMillis;
	}

	/**
	 * Deep copy constructor.
	 *
	 * @param other
	 *            The other task state.
	 */
	public TaskStateInternal(TaskStateInternal other) {
		this.id = other.id;
		if (null == other.parentTaskState) {
			this.parentTaskState = null;
		} else {
			this.parentTaskState = new TaskStateInternal(other.parentTaskState);
		}
		this.taskFamilyId = other.taskFamilyId;
		this.stackLevel = other.stackLevel;
		this.submissionDateEpochMillis = other.submissionDateEpochMillis;
		this.executorAssignmentState = other.executorAssignmentState;
		this.assignedExecutorId = other.assignedExecutorId;
		this.assignedThreadId = other.assignedThreadId;
		this.executorAssignedDateEpochMillis = other.executorAssignedDateEpochMillis;
		this.executionStartDateEpochMillis = other.executionStartDateEpochMillis;
		this.executionEndDateEpochMillis = other.executionEndDateEpochMillis;
	}

	@Override
	public long getId() {
		return id;
	}

	@Override
	public TaskState getParentTaskState() {
		return parentTaskState;
	}

	@Override
	public long getTaskFamilyId() {
		return taskFamilyId;
	}

	@Override
	public long getStackLevel() {
		return stackLevel;
	}

	@Override
	public long getSubmissionDateEpochMillis() {
		return submissionDateEpochMillis;
	}

	@Override
	public TaskExecutorAssignmentState getExecutorAssignmentState() {
		return executorAssignmentState;
	}

	public void setExecutorAssignmentState(TaskExecutorAssignmentState executorAssignmentState) {
		this.executorAssignmentState = executorAssignmentState;
	}

	@Override
	public long getAssignedExecutorId() {
		return assignedExecutorId;
	}

	public void setAssignedExecutorId(long assignedExecutorId) {
		this.assignedExecutorId = assignedExecutorId;
	}

	@Override
	public long getAssignedThreadId() {
		return assignedThreadId;
	}

	public void setAssignedThreadId(long assignedThreadId) {
		this.assignedThreadId = assignedThreadId;
	}

	@Override
	public long getExecutorAssignedDateEpochMillis() {
		return executorAssignedDateEpochMillis;
	}

	public void setExecutorAssignedDateEpochMillis(long executorAssignedDateEpochMillis) {
		this.executorAssignedDateEpochMillis = executorAssignedDateEpochMillis;
	}

	@Override
	public long getExecutionStartDateEpochMillis() {
		return executionStartDateEpochMillis;
	}

	public void setExecutionStartDateEpochMillis(long executionStartDateEpochMillis) {
		this.executionStartDateEpochMillis = executionStartDateEpochMillis;
	}

	@Override
	public long getExecutionEndDateEpochMillis() {
		return executionEndDateEpochMillis;
	}

	public void setExecutionEndDateEpochMillis(long executionEndDateEpochMillis) {
		this.executionEndDateEpochMillis = executionEndDateEpochMillis;
	}

	@Override
	public String toString() {
		return new StringBuilder()//
				.append("TaskState[id=").append(String.format(Locale.US, "%,d", id))//
				.append(", parent=").append(getParentTaskId())//
				.append(", family=").append(String.format(Locale.US, "%,d", taskFamilyId))//
				.append(", stackLevel=").append(stackLevel)//
				.append(", assignedExecutor=").append(assignedExecutorId).append("(").append(executorAssignmentState).append(")")//
				.append(", assignedThread=").append(String.format(Locale.US, "%,d", assignedThreadId))//
				.append(", submitted=").append(String.format(Locale.US, "%,d", submissionDateEpochMillis))//
				.append(", execAssigned=").append(String.format(Locale.US, "%,d", executorAssignedDateEpochMillis))//
				.append(", started=").append(String.format(Locale.US, "%,d", executionStartDateEpochMillis))//
				.append(", finished=").append(String.format(Locale.US, "%,d", executionEndDateEpochMillis))//
				.append("]")//
				.toString();
	}
}
