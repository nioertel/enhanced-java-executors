package io.github.nioertel.async.task.registry.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public class LoggingTaskRegistryStateChangeListener implements StateChangeListener<TaskRegistryState> {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingTaskRegistryStateChangeListener.class);

	@Override
	public void stateChanged(String triggeringOperation, TaskRegistryState newState) {
		LOGGER.info("{}", newState);
	}

}
