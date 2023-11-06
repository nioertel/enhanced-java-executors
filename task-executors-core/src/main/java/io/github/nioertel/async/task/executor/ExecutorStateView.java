package io.github.nioertel.async.task.executor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BooleanSupplier;

class ExecutorStateView {

	private final ThreadPoolExecutor executor;

	private final BooleanSupplier activeChecker;

	ExecutorStateView(ThreadPoolExecutor executor, BooleanSupplier activeChecker) {
		this.executor = executor;
		this.activeChecker = activeChecker;
	}

	public int getMaximumPoolSize() {
		return executor.getMaximumPoolSize();
	}

	public boolean isActive() {
		return activeChecker.getAsBoolean();
	}
}
