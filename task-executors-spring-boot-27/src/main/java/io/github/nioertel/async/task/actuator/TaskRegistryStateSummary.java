package io.github.nioertel.async.task.actuator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.nioertel.async.task.registry.TaskRegistryState;

public class TaskRegistryStateSummary {

	private final long stateVersion;

	private final long lastUsedTaskId;

	private final long lastUsedTaskFamilyId;

	/**
	 * @return The tasks which are currently waiting for execution or running.
	 *
	 *         Key: Task ID
	 *         Value: Task Info
	 */
	private final Map<Long, TaskStateSummary> currentlySubmittedTasks;

	/**
	 * @return The tasks which are currently running.
	 *
	 *         Key: Task ID
	 */
	private final Set<Long> currentlyExecutingTasks;

	private final Map<Long, Long> threadIdTaskIdMappings;

	private final Map<Long, Map<Long, Set<Long>>> currentlyAssignedTasksByExecutorAndTaskFamily;

	private final Set<Long> currentlyParkedTasks;

	// @JsonCreator
	// public TaskRegistryStateSummary(//
	// @JsonProperty("stateVersion") long stateVersion//
	// ) {
	// this.stateVersion = stateVersion;
	// }

	@JsonCreator
	public TaskRegistryStateSummary(//
			@JsonProperty("stateVersion") long stateVersion, //
			@JsonProperty("lastUsedTaskId") long lastUsedTaskId, //
			@JsonProperty("lastUsedTaskFamilyId") long lastUsedTaskFamilyId, //
			@JsonProperty("currentlySubmittedTasks") Map<Long, TaskStateSummary> currentlySubmittedTasks, //
			@JsonProperty("currentlyExecutingTasks") Set<Long> currentlyExecutingTasks, //
			@JsonProperty("threadIdTaskIdMappings") Map<Long, Long> threadIdTaskIdMappings, //
			@JsonProperty("currentlyAssignedTasksByExecutorAndTaskFamily") Map<Long, Map<Long, Set<Long>>> currentlyAssignedTasksByExecutorAndTaskFamily, //
			@JsonProperty("currentlyParkedTasks") Set<Long> currentlyParkedTasks//
	) {
		this.stateVersion = stateVersion;
		this.lastUsedTaskId = lastUsedTaskId;
		this.lastUsedTaskFamilyId = lastUsedTaskFamilyId;
		this.currentlySubmittedTasks = new LinkedHashMap<>(currentlySubmittedTasks);
		this.currentlyExecutingTasks = new LinkedHashSet<>(currentlyExecutingTasks);
		this.threadIdTaskIdMappings = new LinkedHashMap<>(threadIdTaskIdMappings);
		this.currentlyAssignedTasksByExecutorAndTaskFamily = new LinkedHashMap<>();
		currentlyAssignedTasksByExecutorAndTaskFamily.forEach((executorId, tasksByFamily) -> {
			Map<Long, Set<Long>> copiedTasksByFamily = new LinkedHashMap<>();
			tasksByFamily.forEach((familyId, tasks) -> {
				copiedTasksByFamily.put(familyId, new LinkedHashSet<>(tasks));
			});
			this.currentlyAssignedTasksByExecutorAndTaskFamily.put(executorId, copiedTasksByFamily);
		});
		this.currentlyParkedTasks = new LinkedHashSet<>(currentlyParkedTasks);
	}

	public TaskRegistryStateSummary(TaskRegistryState state) {
		this.stateVersion = state.getStateVersion();
		this.lastUsedTaskId = state.getLastUsedTaskId();
		this.lastUsedTaskFamilyId = state.getLastUsedTaskFamilyId();
		this.currentlySubmittedTasks = new LinkedHashMap<>();
		state.getCurrentlySubmittedTasks().forEach((taskId, taskState) -> {
			this.currentlySubmittedTasks.put(taskId, new TaskStateSummary(taskState));
		});
		this.currentlyExecutingTasks = new LinkedHashSet<>(state.getCurrentlyExecutingTasks());
		this.threadIdTaskIdMappings = new LinkedHashMap<>(state.getThreadIdTaskIdMappings());
		this.currentlyAssignedTasksByExecutorAndTaskFamily = new LinkedHashMap<>();
		state.getCurrentlyAssignedTasksByExecutorAndTaskFamily().forEach((executorId, tasksByFamily) -> {
			Map<Long, Set<Long>> copiedTasksByFamily = new LinkedHashMap<>();
			tasksByFamily.forEach((familyId, tasks) -> {
				copiedTasksByFamily.put(familyId, new LinkedHashSet<>(tasks));
			});
			this.currentlyAssignedTasksByExecutorAndTaskFamily.put(executorId, copiedTasksByFamily);
		});
		this.currentlyParkedTasks = new LinkedHashSet<>(state.getCurrentlyParkedTasks());
	}

	public long getStateVersion() {
		return stateVersion;
	}

	public long getLastUsedTaskId() {
		return lastUsedTaskId;
	}

	public long getLastUsedTaskFamilyId() {
		return lastUsedTaskFamilyId;
	}

	public Map<Long, TaskStateSummary> getCurrentlySubmittedTasks() {
		return Collections.unmodifiableMap(currentlySubmittedTasks);
	}

	public Set<Long> getCurrentlyExecutingTasks() {
		return Collections.unmodifiableSet(currentlyExecutingTasks);
	}

	public Map<Long, Long> getThreadIdTaskIdMappings() {
		return Collections.unmodifiableMap(threadIdTaskIdMappings);
	}

	public Map<Long, Map<Long, Set<Long>>> getCurrentlyAssignedTasksByExecutorAndTaskFamily() {
		return Collections.unmodifiableMap(currentlyAssignedTasksByExecutorAndTaskFamily);
	}

	public Set<Long> getCurrentlyParkedTasks() {
		return Collections.unmodifiableSet(currentlyParkedTasks);
	}

}
