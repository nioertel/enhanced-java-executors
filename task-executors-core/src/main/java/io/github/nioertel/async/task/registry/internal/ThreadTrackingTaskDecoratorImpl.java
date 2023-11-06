package io.github.nioertel.async.task.registry.internal;

import java.util.concurrent.Callable;

import io.github.nioertel.async.task.registry.IdentifiableCallable;

/**
 * Task decorator that records runnable information.
 *
 * @author niels.oertel
 */
class ThreadTrackingTaskDecoratorImpl implements ThreadTrackingTaskDecorator {

	private final TaskRegistry taskRegistry;

	public ThreadTrackingTaskDecoratorImpl(TaskRegistry taskRegistry) {
		this.taskRegistry = taskRegistry;
	}

	@Override
	public ThreadTrackingRunnable decorate(Runnable runnable) {
		return new ThreadTrackingRunnable(runnable, taskRegistry);
	}

	@Override
	public <T> IdentifiableCallable<T> decorate(Callable<T> task) {
		return new ThreadTrackingCallable<>(task, taskRegistry);
	}

}