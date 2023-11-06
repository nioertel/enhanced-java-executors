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

	/**
	 * This test runs a task family where:
	 * <ul>
	 * <li>Task 1 is submitted from the main thread</li>
	 * <li>Task 2 is submitted by task 1</li>
	 * <li>Task 3 is submitted by task 2</li>
	 * </ul>
	 */
	@Test
	void testRunThreeLevelTaskFamily() throws InterruptedException, ExecutionException {
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
		executor.setBurstExecutionMDOP(1);

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
	 * Idea of this test:
	 * <ul>
	 * <li>We have an executor with two threads in its core pool</li>
	 * <li>We submit two tasks which will occupy these two threads</li>
	 * <li>Each of these tasks will spawn another task, which should both be sent to the burst executor as the main executor
	 * is occupied</li>
	 * <li>The burst executor has two threads in its core pool but should still not run the level two tasks in parallel as
	 * it the burst MDOP is set to 1</li>
	 * </ul>
	 */
	@Test
	void testParkingAndResubmission() throws InterruptedException, ExecutionException {
		BurstingThreadPoolExecutor executor = BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
				2 // corePoolSize
				, 2 // maximumPoolSize
				, 2 // burstCorePoolSize
				, 2 // burstMaximumPoolSize
				, 60 // keepAliveTime
				, TimeUnit.SECONDS // unit
				, new LinkedBlockingQueue<>(10) // workQueue
				, new LinkedBlockingQueue<>(10) // burstWorkQueue
				, new NamedThreadFactory("junit") // threadFactory
				, new ThreadPoolExecutor.AbortPolicy() // rejectedExecutionHandler
		);
		executor.setBurstExecutionMDOP(1);
		executor.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());

		try {
			// family 1
			ControllableTestTask task13 = new ControllableTestTask("Test task 13");
			ControllableTestTask task12 = new AsynchTaskExecutingControllableTestTask("Test task 12", task13, executor);
			ControllableTestTask task11 = new AsynchTaskExecutingControllableTestTask("Test task 11", task12, executor);

			// family 2
			ControllableTestTask task23 = new ControllableTestTask("Test task 23");
			ControllableTestTask task22 = new AsynchTaskExecutingControllableTestTask("Test task 22", task23, executor);
			ControllableTestTask task21 = new AsynchTaskExecutingControllableTestTask("Test task 21", task22, executor);

			// nothing should be running yet
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();

			// submit first task of family 1 and wait until task has entered its main method
			Future<?> result11 = executor.submit(task11);
			task11.waitUntilStarted();
			// task should now appear in registry
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).hasSize(1);

			// submit first task of family 2 and wait until task has entered its main method
			Future<?> result21 = executor.submit(task21);
			task21.waitUntilStarted();
			// task should now appear in registry
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).hasSize(2);

			// let the main tasks run through its logic
			task11.allowStart();
			task21.allowStart();

			// wait for processing to finish
			task11.allowFinish();
			task21.allowFinish();

			// wait until the scheduler publishes the result
			result11.get();
			result21.get();

			// nothing should be running anymore
			BDDAssertions.assertThat(executor.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();
		} finally {
			executor.shutdown();
		}
	}

}
