package io.github.nioertel.async.task.registry.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;

class TaskRegistryStateInternal implements TaskRegistryState {

	// TODO: consider moving task details into a task object with read-only interface
	// TODO: Also harmonize TaskSubmissionResult and TaskDetails

	final AtomicLong taskIdProvider;

	final AtomicLong taskFamilyIdProvider;

	/**
	 * The tasks which are currently waiting for execution or running.
	 *
	 * Key: Task ID
	 * Value: The task state.
	 */
	final Map<Long, TaskStateInternal> currentlySubmittedTasks;

	/**
	 * The tasks which are currently running.
	 *
	 * Key: Task ID
	 */
	final Set<Long> currentlyExecutingTasks;

	/**
	 * A map containing all currently running tasks (values) with their executing threads (keys).
	 *
	 * Key: Thread ID
	 * Value: Task ID
	 */
	final Map<Long, Long> threadIdTaskIdMappings;

	/**
	 * 
	 */
	final Map<Long, Set<Long>> currentlyExecutingTasksByTaskFamily;

	/**
	 * The tasks that have been parked during executor assignment.
	 */
	final List<Long> currentlyParkedTasks;

	/**
	 * Constructor.
	 */
	public TaskRegistryStateInternal() {
		this.taskIdProvider = new AtomicLong();
		this.taskFamilyIdProvider = new AtomicLong();
		this.currentlySubmittedTasks = new LinkedHashMap<>();
		this.currentlyExecutingTasks = new LinkedHashSet<>();
		this.threadIdTaskIdMappings = new LinkedHashMap<>();
		this.currentlyExecutingTasksByTaskFamily = new LinkedHashMap<>();
		this.currentlyParkedTasks = new ArrayList<>();
	}

	/**
	 * Copy constructor.
	 *
	 * @param source
	 *            The source to copy from.
	 */
	public TaskRegistryStateInternal(TaskRegistryStateInternal source) {
		this.taskIdProvider = new AtomicLong(source.taskIdProvider.get());
		this.taskFamilyIdProvider = new AtomicLong(source.taskFamilyIdProvider.get());
		this.currentlySubmittedTasks = new LinkedHashMap<>();
		source.currentlySubmittedTasks.forEach((id, taskState) -> {
			currentlySubmittedTasks.put(id, new TaskStateInternal(taskState));
		});
		this.currentlyExecutingTasks = new LinkedHashSet<>(source.currentlyExecutingTasks);
		this.threadIdTaskIdMappings = new LinkedHashMap<>(source.threadIdTaskIdMappings);
		this.currentlyExecutingTasksByTaskFamily = new LinkedHashMap<>(source.currentlyExecutingTasksByTaskFamily);
		this.currentlyParkedTasks = new ArrayList<>(source.currentlyParkedTasks);
	}

	@Override
	public long getLastUsedTaskId() {
		return taskIdProvider.get();
	}

	@Override
	public long getLastUsedTaskFamilyId() {
		return taskFamilyIdProvider.get();
	}

	@Override
	public Map<Long, TaskState> getCurrentlySubmittedTasks() {
		return Collections.unmodifiableMap(currentlySubmittedTasks);
	}

	@Override
	public Set<Long> getCurrentlyExecutingTasks() {
		return Collections.unmodifiableSet(currentlyExecutingTasks);
	}

	@Override
	public Map<Long, Long> getThreadIdTaskIdMappings() {
		return Collections.unmodifiableMap(threadIdTaskIdMappings);
	}

	@Override
	public Map<Long, Set<Long>> getCurrentlyExecutingTasksByTaskFamily() {
		return Collections.unmodifiableMap(currentlyExecutingTasksByTaskFamily);
	}

	@Override
	public List<Long> getCurrentlyParkedTasks() {
		return Collections.unmodifiableList(currentlyParkedTasks);
	}

	@Override
	public TaskState getTaskState(long taskId) {
		return currentlySubmittedTasks.get(taskId);
	}

}