package io.github.nioertel.async.task.executor;

public interface MultiExecutorState {

	int getMaximumPoolSize(long executorId);

	boolean isActive(long executorId);

}
