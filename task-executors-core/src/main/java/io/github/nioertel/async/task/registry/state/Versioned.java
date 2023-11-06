package io.github.nioertel.async.task.registry.state;

public interface Versioned {

	void incrementVersion();

	long getVersion();

}
