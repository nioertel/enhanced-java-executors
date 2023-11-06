package io.github.nioertel.async.task.registry.internal;

import java.util.concurrent.Callable;

import io.github.nioertel.async.task.registry.IdentifiableCallable;
import io.github.nioertel.async.task.registry.IdentifiableRunnable;

public interface ThreadTrackingTaskDecorator {

	IdentifiableRunnable decorate(Runnable task);

	<T> IdentifiableCallable<T> decorate(Callable<T> task);
}
