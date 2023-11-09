package io.github.nioertel.async.test;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Throwable> {

	public T get() throws E;
}
