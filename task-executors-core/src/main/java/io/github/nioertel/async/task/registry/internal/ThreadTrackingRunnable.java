package io.github.nioertel.async.task.registry.internal;

import io.github.nioertel.async.task.registry.IdentifiableRunnable;

class ThreadTrackingRunnable implements IdentifiableRunnable {

	private final TaskRegistry taskRegistry;

	private final Runnable delegate;

	private final long id;

	public ThreadTrackingRunnable(Runnable delegate, TaskRegistry taskRegistry) {
		this.taskRegistry = taskRegistry;
		this.delegate = delegate;
		this.id = taskRegistry.generateNewTaskId();
	}

	@Override
	public void run() {
		try {
			taskRegistry.taskExecutionStarted(this, Thread.currentThread());
			delegate.run();
		} finally {
			taskRegistry.taskExecutionFinished(this);
		}
	}

	@Override
	public long getId() {
		return id;
	}
}
