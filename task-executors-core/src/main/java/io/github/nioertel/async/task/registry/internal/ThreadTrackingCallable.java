package io.github.nioertel.async.task.registry.internal;

import java.util.concurrent.Callable;

import io.github.nioertel.async.task.registry.IdentifiableCallable;

class ThreadTrackingCallable<T> implements IdentifiableCallable<T> {

	private final TaskRegistry taskRegistry;

	private final Callable<T> delegate;

	private final long id;

	public ThreadTrackingCallable(Callable<T> delegate, TaskRegistry taskRegistry) {
		this.taskRegistry = taskRegistry;
		this.delegate = delegate;
		this.id = taskRegistry.generateNewTaskId();
	}

	@Override
	public T call() throws Exception {
		try {
			taskRegistry.taskExecutionStarted(this, Thread.currentThread());
			return delegate.call();
		} finally {
			taskRegistry.taskExecutionFinished(this);
		}
	}

	@Override
	public long getId() {
		return id;
	}
}
