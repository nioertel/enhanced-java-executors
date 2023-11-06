package io.github.nioertel.async.task.registry.state;

public interface StateChangeListener<T> {

	void stateChanged(String triggeringOperation, T newState);
}