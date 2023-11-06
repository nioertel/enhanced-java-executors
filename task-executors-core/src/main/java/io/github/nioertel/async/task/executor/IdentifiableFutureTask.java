package io.github.nioertel.async.task.executor;

import java.util.concurrent.FutureTask;

import io.github.nioertel.async.task.registry.IdentifiableCallable;
import io.github.nioertel.async.task.registry.IdentifiableRunnable;

class IdentifiableFutureTask<T> extends FutureTask<T> implements IdentifiableRunnable {

	private final long id;

	public IdentifiableFutureTask(IdentifiableCallable<T> callable) {
		super(callable);
		this.id = callable.getId();
	}

	public IdentifiableFutureTask(IdentifiableRunnable runnable, T result) {
		super(runnable, result);
		this.id = runnable.getId();
	}

	@Override
	public long getId() {
		return id;
	}

}