package io.github.nioertel.async.task.registry.listeners;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public class LoggingMetricsStateChangeListener implements StateChangeListener<TaskRegistryMetrics> {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoggingMetricsStateChangeListener.class);

	@Override
	public void stateChanged(String triggeringOperation, TaskRegistryMetrics newState) {
		LOGGER.info("{}", newState);
	}

}
