package io.github.nioertel.async.task.registry.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.state.Versioned;

class TaskRegistryStateInternal implements TaskRegistryState, Versioned {

	final AtomicLong stateVersion;

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
	 * A map containing the currently assigned tasks by executor and task family.
	 *
	 * Key: Executor ID
	 * Value: Map with key = Task Family ID and value = Task IDs
	 */
	final Map<Long, Map<Long, Set<Long>>> currentlyAssignedTasksByExecutorAndTaskFamily;

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
	 * The tasks that have been parked during executor assignment.
	 */
	final Set<Long> currentlyParkedTasks;

	/**
	 * Constructor.
	 */
	public TaskRegistryStateInternal() {
		this.stateVersion = new AtomicLong();
		this.taskIdProvider = new AtomicLong();
		this.taskFamilyIdProvider = new AtomicLong();
		this.currentlySubmittedTasks = new LinkedHashMap<>();
		this.currentlyExecutingTasks = new LinkedHashSet<>();
		this.threadIdTaskIdMappings = new LinkedHashMap<>();
		this.currentlyAssignedTasksByExecutorAndTaskFamily = new LinkedHashMap<>();
		this.currentlyParkedTasks = new LinkedHashSet<>();
	}

	/**
	 * Copy constructor.
	 *
	 * @param source
	 *            The source to copy from.
	 */
	public TaskRegistryStateInternal(TaskRegistryStateInternal source) {
		this.stateVersion = new AtomicLong(source.stateVersion.get());
		this.taskIdProvider = new AtomicLong(source.taskIdProvider.get());
		this.taskFamilyIdProvider = new AtomicLong(source.taskFamilyIdProvider.get());
		this.currentlySubmittedTasks = new LinkedHashMap<>();
		source.currentlySubmittedTasks.forEach((id, taskState) -> {
			currentlySubmittedTasks.put(id, new TaskStateInternal(taskState));
		});
		this.currentlyExecutingTasks = new LinkedHashSet<>(source.currentlyExecutingTasks);
		this.threadIdTaskIdMappings = new LinkedHashMap<>(source.threadIdTaskIdMappings);
		this.currentlyAssignedTasksByExecutorAndTaskFamily = new LinkedHashMap<>();
		source.currentlyAssignedTasksByExecutorAndTaskFamily.forEach((executorId, taskIdsByFamilyId) -> {
			Map<Long, Set<Long>> currentlyAssignedTasksForExecutorByTaskFamily = new LinkedHashMap<>();
			taskIdsByFamilyId.forEach((familyId, taskIds) -> {
				currentlyAssignedTasksForExecutorByTaskFamily.put(familyId, new LinkedHashSet<>(taskIds));
			});
			this.currentlyAssignedTasksByExecutorAndTaskFamily.put(executorId, currentlyAssignedTasksForExecutorByTaskFamily);
		});
		this.currentlyParkedTasks = new LinkedHashSet<>(source.currentlyParkedTasks);
	}

	@Override
	public long getStateVersion() {
		return stateVersion.get();
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
	public Map<Long, Map<Long, Set<Long>>> getCurrentlyAssignedTasksByExecutorAndTaskFamily() {
		return Collections.unmodifiableMap(currentlyAssignedTasksByExecutorAndTaskFamily);
	}

	@Override
	public Map<Long, Set<Long>> getCurrentlyAssignedTasksByTaskFamilyForExecutor(long executorId) {
		return Collections.unmodifiableMap(currentlyAssignedTasksByExecutorAndTaskFamily.getOrDefault(executorId, Map.of()));
	}

	@Override
	public Set<Long> getCurrentlyParkedTasks() {
		return Collections.unmodifiableSet(currentlyParkedTasks);
	}

	@Override
	public TaskState getTaskState(long taskId) {
		return currentlySubmittedTasks.get(taskId);
	}

	@Override
	public void incrementVersion() {
		stateVersion.incrementAndGet();
	}

	@Override
	public long getVersion() {
		return stateVersion.get();
	}

}