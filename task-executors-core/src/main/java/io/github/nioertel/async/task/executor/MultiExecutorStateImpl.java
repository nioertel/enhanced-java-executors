package io.github.nioertel.async.task.executor;

import java.util.LinkedHashMap;
import java.util.Map;

class MultiExecutorStateImpl implements MultiExecutorState {

	private final Map<Long, ExecutorStateView> executorStateViews;

	MultiExecutorStateImpl(Map<Long, ExecutorStateView> executorStateViews) {
		this.executorStateViews = new LinkedHashMap<>(executorStateViews);
	}

	@Override
	public int getMaximumPoolSize(long executorId) {
		assertExecutorAvailable(executorId, "getMaximumPoolSize");
		return executorStateViews.get(executorId).getMaximumPoolSize();
	}

	@Override
	public boolean isActive(long executorId) {
		assertExecutorAvailable(executorId, "isActive");
		return executorStateViews.get(executorId).isActive();
	}

	private void assertExecutorAvailable(long executorId, String pendingOperation) {
		if (!executorStateViews.containsKey(executorId)) {
			throw new IllegalStateException(
					"Cannot execute operation [" + pendingOperation + "] on executor with id " + executorId + ". No such executor available.");
		}
	}
}
