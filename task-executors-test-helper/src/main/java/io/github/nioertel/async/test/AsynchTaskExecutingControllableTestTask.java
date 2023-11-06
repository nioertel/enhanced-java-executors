package io.github.nioertel.async.test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class AsynchTaskExecutingControllableTestTask extends ControllableTestTask {

	public AsynchTaskExecutingControllableTestTask(String name, ControllableTestTask delegate, ExecutorService asynchExecutor) {
		super(name, () -> {
			Future<?> result = asynchExecutor.submit(delegate);
			delegate.allowStart();
			delegate.allowFinish();
			try {
				delegate.waitUntilFinished();
				result.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Waiting for task 2 to finish was interrupted.");
			} catch (ExecutionException e) {
				throw new IllegalStateException("Execution of task 2 failed unexepectedly.", e);
			}
		});
	}

	
}