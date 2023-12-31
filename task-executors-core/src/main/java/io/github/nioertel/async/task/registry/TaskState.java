package io.github.nioertel.async.task.registry;

import io.github.nioertel.async.task.registry.internal.TaskProgress;

public interface TaskState {

	TaskState getParentTaskState();

	long getId();

	long getTaskFamilyId();

	long getStackLevel();

	long getSubmissionDateEpochMillis();

	TaskProgress getTaskProgress();

	long getAssignedExecutorId();

	long getAssignedThreadId();

	long getExecutorAssignedDateEpochMillis();

	long getExecutionStartDateEpochMillis();

	long getExecutionEndDateEpochMillis();

	default long getParentTaskAssignedExecutorId() {
		TaskState parentTaskState = getParentTaskState();
		if (null == parentTaskState) {
			return -1;
		} else {
			return parentTaskState.getAssignedExecutorId();
		}
	}

	default long getParentTaskId() {
		TaskState parentTaskState = getParentTaskState();
		if (null == parentTaskState) {
			return -1;
		} else {
			return parentTaskState.getId();
		}
	}
}
