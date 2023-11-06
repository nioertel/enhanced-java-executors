package io.github.nioertel.async.task.registry.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public class LoggingTaskRegistryStateChangeListener implements StateChangeListener<TaskRegistryState> {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingTaskRegistryStateChangeListener.class);

	@Override
	public void stateChanged(String triggeringOperation, TaskRegistryState newState) {
		StringBuilder sb = new StringBuilder()//
				.append("Current state:").append(System.lineSeparator())//
				.append("  Current version: ").append(newState.getStateVersion()).append(System.lineSeparator())//
				.append("  Last operation: ").append(triggeringOperation).append(System.lineSeparator())//
				.append("  Last provided task id: ").append(newState.getLastUsedTaskId()).append(System.lineSeparator())//
				.append("  Last provided task family id: ").append(newState.getLastUsedTaskFamilyId()).append(System.lineSeparator())//
				.append("  Currently submitted tasks: ").append(System.lineSeparator());
		for (TaskState taskState : newState.getCurrentlySubmittedTasks().values()) {
			sb.append("    ").append(taskState).append(System.lineSeparator());
		}
		sb//
				.append("  Currently submitted tasks by executor + family: ").append(newState.getCurrentlyAssignedTasksByExecutorAndTaskFamily())
				.append(System.lineSeparator())//
				.append("  Currently executing tasks: ").append(newState.getCurrentlyExecutingTasks()).append(System.lineSeparator())//
				.append("  Currently parked tasks: ").append(newState.getCurrentlyParkedTasks()).append(System.lineSeparator());
		LOGGER.info("{}", sb);
	}

}
