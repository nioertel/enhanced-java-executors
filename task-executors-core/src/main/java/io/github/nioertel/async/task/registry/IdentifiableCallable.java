package io.github.nioertel.async.task.registry;

import java.util.concurrent.Callable;

public interface IdentifiableCallable<T> extends Identifiable, Callable<T> {

}
