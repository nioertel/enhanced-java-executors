package io.github.nioertel.async.test;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class NamedThreadFactory implements ThreadFactory {

	private static final AtomicLong THREAD_FACTORY_ID_GENERATOR = new AtomicLong();

	private final long threadFactoryId = THREAD_FACTORY_ID_GENERATOR.incrementAndGet();

	private final String namePrefix;

	private final AtomicLong threadIdGenerator = new AtomicLong();

	public NamedThreadFactory(String namePrefix) {
		this.namePrefix = namePrefix;
	}

	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r);
		t.setName(namePrefix + "-" + threadFactoryId + "-" + threadIdGenerator.incrementAndGet());
		return t;
	}

}