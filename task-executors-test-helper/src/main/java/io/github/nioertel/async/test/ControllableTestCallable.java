package io.github.nioertel.async.test;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControllableTestCallable<T> implements Callable<T> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final AtomicLong currentStep = new AtomicLong();

	private final Semaphore runControlSemaphore = new Semaphore(0, true);

	private final Semaphore runPublishSemaphore = new Semaphore(0, true);

	private final String name;

	private final Callable<T> delegate;

	private final AtomicBoolean startAllowed = new AtomicBoolean();

	private final AtomicBoolean finishAllowed = new AtomicBoolean();

	public ControllableTestCallable(String name) {
		this.name = name;
		this.delegate = () -> {
			logger.info("Task [{}] running.", name);
			return null;
		};
	}

	public ControllableTestCallable(String name, Callable<T> delegate) {
		this.name = name;
		this.delegate = delegate;
	}

	private void allowStep(String name, AtomicBoolean stateMarker) {
		if (stateMarker.compareAndSet(false, true)) {
			runControlSemaphore.release();
		} else {
			throw new IllegalStateException("Cannot allow step [" + name + "], as state is already reached.");
		}
	}

	public void allowStart() {
		allowStep("start", startAllowed);
	}

	public void allowFinish() {
		allowStep("finish", finishAllowed);
	}

	public void waitUntilStarted() throws InterruptedException {
		waitForStep(1L);
	}

	public void waitUntilRunning() throws InterruptedException {
		waitForStep(2L);
	}

	public void waitUntilReadyToFinish() throws InterruptedException {
		waitForStep(3L);
	}

	public void waitUntilFinished() throws InterruptedException {
		waitForStep(4L);
	}

	private void waitForStep(long stepId) throws InterruptedException {
		while (currentStep.get() < stepId) {
			runPublishSemaphore.acquire();
			synchronized (currentStep) {
				logger.info("Task [{}] is now at step {}.", name, currentStep.get());
			}
		}
	}

	private void progressRun() {
		synchronized (currentStep) {
			runPublishSemaphore.release();
			currentStep.incrementAndGet();
		}
	}

	@Override
	public T call() throws Exception {
		progressRun();
		logger.info("Task [{}] waiting for execution to start.", name);

		runControlSemaphore.acquire();

		progressRun();
		logger.info("Task [{}] started.", name);

		runControlSemaphore.acquire();
		T result = delegate.call();
		progressRun();
		logger.info("Task [{}] finished.", name);
		progressRun();
		return result;
	}

	@Override
	public String toString() {
		return "Task: " + name;
	}
}