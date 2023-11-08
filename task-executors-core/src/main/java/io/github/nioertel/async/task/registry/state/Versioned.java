package io.github.nioertel.async.task.registry.state;

public interface Versioned {

	void setLastOperation(String lastOperation);

	void incrementVersion();

	long getVersion();

}
