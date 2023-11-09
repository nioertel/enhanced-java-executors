package io.github.nioertel.async.task.actuator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.internal.TaskProgress;

public class TaskStateSummary {

	private final long taskId;

	private final long taskFamilyId;

	private final long stackLevel;

	private final long submissionDateEpochMillis;

	private final TaskProgress taskProgress;

	private final long assignedExecutorId;

	private final long assignedThreadId;

	private final long executorAssignedDateEpochMillis;

	private final long executionStartDateEpochMillis;

	private final long executionEndDateEpochMillis;

	private final TaskStateSummary parentTaskState;

	@JsonCreator
	public TaskStateSummary(//
			@JsonProperty("taskId") long taskId, //
			@JsonProperty("taskFamilyId") long taskFamilyId, //
			@JsonProperty("stackLevel") long stackLevel, //
			@JsonProperty("submissionDateEpochMillis") long submissionDateEpochMillis, //
			@JsonProperty("taskProgress") TaskProgress taskProgress, //
			@JsonProperty("assignedExecutorId") long assignedExecutorId, //
			@JsonProperty("assignedThreadId") long assignedThreadId, //
			@JsonProperty("executorAssignedDateEpochMillis") long executorAssignedDateEpochMillis, //
			@JsonProperty("executionStartDateEpochMillis") long executionStartDateEpochMillis, //
			@JsonProperty("executionEndDateEpochMillis") long executionEndDateEpochMillis, //
			@JsonProperty("parentTaskState") TaskStateSummary parentTaskState//
	) {
		this.taskId = taskId;
		this.taskFamilyId = taskFamilyId;
		this.stackLevel = stackLevel;
		this.submissionDateEpochMillis = submissionDateEpochMillis;
		this.taskProgress = taskProgress;
		this.assignedExecutorId = assignedExecutorId;
		this.assignedThreadId = assignedThreadId;
		this.executorAssignedDateEpochMillis = executorAssignedDateEpochMillis;
		this.executionStartDateEpochMillis = executionStartDateEpochMillis;
		this.executionEndDateEpochMillis = executionEndDateEpochMillis;
		this.parentTaskState = parentTaskState;
	}

	public TaskStateSummary(TaskState state) {
		this.taskId = state.getId();
		this.taskFamilyId = state.getTaskFamilyId();
		this.stackLevel = state.getStackLevel();
		this.submissionDateEpochMillis = state.getSubmissionDateEpochMillis();
		this.taskProgress = state.getTaskProgress();
		this.assignedExecutorId = state.getAssignedExecutorId();
		this.assignedThreadId = state.getAssignedThreadId();
		this.executorAssignedDateEpochMillis = state.getExecutorAssignedDateEpochMillis();
		this.executionStartDateEpochMillis = state.getExecutionStartDateEpochMillis();
		this.executionEndDateEpochMillis = state.getExecutionEndDateEpochMillis();
		if (null == state.getParentTaskState()) {
			this.parentTaskState = null;
		} else {
			this.parentTaskState = new TaskStateSummary(state.getParentTaskState());
		}
	}

	public long getTaskId() {
		return taskId;
	}

	public long getTaskFamilyId() {
		return taskFamilyId;
	}

	public long getStackLevel() {
		return stackLevel;
	}

	public long getSubmissionDateEpochMillis() {
		return submissionDateEpochMillis;
	}

	public TaskProgress getTaskProgress() {
		return taskProgress;
	}

	public long getAssignedExecutorId() {
		return assignedExecutorId;
	}

	public long getAssignedThreadId() {
		return assignedThreadId;
	}

	public long getExecutorAssignedDateEpochMillis() {
		return executorAssignedDateEpochMillis;
	}

	public long getExecutionStartDateEpochMillis() {
		return executionStartDateEpochMillis;
	}

	public long getExecutionEndDateEpochMillis() {
		return executionEndDateEpochMillis;
	}

	public TaskStateSummary getParentTaskState() {
		return parentTaskState;
	}

}
