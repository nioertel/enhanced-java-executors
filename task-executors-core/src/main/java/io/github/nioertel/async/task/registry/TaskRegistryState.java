package io.github.nioertel.async.task.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TaskRegistryState {

	long getLastUsedTaskId();

	long getLastUsedTaskFamilyId();

	/**
	 * @return The tasks which are currently waiting for execution or running.
	 *
	 *         Key: Task ID
	 *         Value: Task Info
	 */
	Map<Long, TaskState> getCurrentlySubmittedTasks();

	/**
	 * @return The tasks which are currently running.
	 *
	 *         Key: Task ID
	 */
	Set<Long> getCurrentlyExecutingTasks();

	Map<Long, Long> getThreadIdTaskIdMappings();

	Map<Long, Set<Long>> getCurrentlyExecutingTasksByTaskFamily();

	List<Long> getCurrentlyParkedTasks();

	TaskState getTaskState(long taskId);
}
