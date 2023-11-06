package io.github.nioertel.async.task.executor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import io.github.nioertel.async.task.registry.listeners.LoggingTaskRegistryStateChangeListener;
import io.github.nioertel.async.test.AsynchTaskExecutingControllableTestTask;
import io.github.nioertel.async.test.ControllableTestTask;
import io.github.nioertel.async.test.NamedThreadFactory;

class BurstingThreadPoolExecutorImplTest {

	@Test
	void testRunSimpleTask() throws InterruptedException, ExecutionException {
		BurstingThreadPoolExecutor executor = BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
				1 // corePoolSize
				, 1 // maximumPoolSize
				, 1 // burstCorePoolSize
				, 1 // burstMaximumPoolSize
				, 60 // keepAliveTime
				, TimeUnit.SECONDS // unit
				, new LinkedBlockingQueue<>(10) // workQueue
				, new LinkedBlockingQueue<>(10) // burstWorkQueue
				, new NamedThreadFactory("junit") // threadFactory
				, new ThreadPoolExecutor.AbortPolicy() // rejectedExecutionHandler
		);
		executor.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());

		try {
			ControllableTestTask task1 = new ControllableTestTask("Test task 1");

			// nothing should be running yet
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();

			// submit first task and wait until task has entered its main method
			Future<?> result1 = executor.submit(task1);
			task1.waitUntilStarted();
			// task should now appear in registry
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).hasSize(1);

			// let the task run through its logic
			task1.allowStart();

			// wait for processing to finish
			task1.allowFinish();

			// wait until the scheduler publishes the result
			result1.get();

			// nothing should be running anymore
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();
		} finally {
			executor.shutdown();
		}
	}

	@Test
	void testRunTwoLevelTask() throws InterruptedException, ExecutionException {
		BurstingThreadPoolExecutor executor = BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
				1 // corePoolSize
				, 1 // maximumPoolSize
				, 0 // burstCorePoolSize
				, 1 // burstMaximumPoolSize
				, 60 // keepAliveTime
				, TimeUnit.SECONDS // unit
				, new LinkedBlockingQueue<>(10) // workQueue
				, new LinkedBlockingQueue<>(10) // burstWorkQueue
				, new NamedThreadFactory("junit") // threadFactory
				, new ThreadPoolExecutor.AbortPolicy() // rejectedExecutionHandler
		);
		executor.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());

		try {
			ControllableTestTask task2 = new ControllableTestTask("Test task 2");
			ControllableTestTask task1 = new AsynchTaskExecutingControllableTestTask("Test task 1", task2, executor);

			// nothing should be running yet
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();

			// submit first task and wait until task has entered its main method
			Future<?> result1 = executor.submit(task1);
			task1.waitUntilStarted();
			// task should now appear in registry
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).hasSize(1);

			// let the task run through its logic
			task1.allowStart();

			// wait for processing to finish
			task1.allowFinish();

			// wait until the scheduler publishes the result
			result1.get();

			// nothing should be running anymore
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();
		} finally {
			executor.shutdown();
		}
	}

	@Test
	void testRunThreeLevelTask() throws InterruptedException, ExecutionException {
		BurstingThreadPoolExecutor executor = BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
				1 // corePoolSize
				, 1 // maximumPoolSize
				, 2 // burstCorePoolSize
				, 2 // burstMaximumPoolSize
				, 60 // keepAliveTime
				, TimeUnit.SECONDS // unit
				, new LinkedBlockingQueue<>(10) // workQueue
				, new LinkedBlockingQueue<>(10) // burstWorkQueue
				, new NamedThreadFactory("junit") // threadFactory
				, new ThreadPoolExecutor.AbortPolicy() // rejectedExecutionHandler
		);
		executor.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());

		try {
			ControllableTestTask task3 = new ControllableTestTask("Test task 3");
			ControllableTestTask task2 = new AsynchTaskExecutingControllableTestTask("Test task 2", task3, executor);
			ControllableTestTask task1 = new AsynchTaskExecutingControllableTestTask("Test task 1", task2, executor);

			// nothing should be running yet
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();

			// submit first task and wait until task has entered its main method
			Future<?> result1 = executor.submit(task1);
			task1.waitUntilStarted();
			// task should now appear in registry
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).hasSize(1);

			// let the task run through its logic
			task1.allowStart();

			// wait for processing to finish
			task1.allowFinish();

			// wait until the scheduler publishes the result
			result1.get();

			// nothing should be running anymore
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();
		} finally {
			executor.shutdown();
		}
	}

	/**
	 * Idea of this test: TODO
	 */
	@Test
	void testBurstSubmissionOrderingForMutlipleThreeLevelTasks() {

	}

}
