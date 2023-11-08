package io.github.nioertel.async.task.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

public interface MultiThreadPoolExecutor extends ExecutorService {

	/**
	 * Create a new bursting thread pool executor. For the details on all parameters see
	 * {@link java.util.concurrent.ThreadPoolExecutor}.
	 * <p>
	 * Important info on the relationship between corePoolSize, maximumPoolSize and workQueue:<br />
	 * When we submit a new task to the ThreadPoolTaskExecutor, it creates a new thread if fewer than corePoolSize threads
	 * are running, even if there are idle threads in the pool, or if fewer than maxPoolSize threads are running and the
	 * queue defined by queueCapacity is full.
	 * </p>
	 *
	 * @param corePoolSize
	 *            The core pool size of the main pool.
	 * @param maximumPoolSize
	 *            The maximum pool size of the main pool.
	 * @param secondaryCorePoolSize
	 *            The core pool size of the secondary pool.
	 * @param maximumSecondaryPoolSize
	 *            The maximum pool size of the secondary pool.
	 * @param keepAliveTime
	 *            The keep alive time.
	 * @param unit
	 *            The unit of the keep alive time
	 * @param workQueue
	 *            The work queue.
	 * @param secondaryWorkQueue
	 *            The work queue of the secondary pool.
	 * @param threadFactory
	 *            The thread factory.
	 * @param handler
	 *            The rejected execution handler.
	 * @param executorIdAssignerCreator
	 *            The function to create the service that assigns the executor for a given task. This will be called already
	 *            during construction.
	 */
	static <T extends ExecutorIdAssigner> MultiThreadPoolExecutor newMultiThreadPoolExecutor(//
			int corePoolSize, //
			int maximumPoolSize, //
			int secondaryCorePoolSize, //
			int maximumSecondaryPoolSize, //
			long keepAliveTime, //
			TimeUnit unit, //
			BlockingQueue<Runnable> workQueue, //
			BlockingQueue<Runnable> secondaryWorkQueue, //
			ThreadFactory threadFactory, //
			RejectedExecutionHandler handler, //
			Function<MultiExecutorState, T> executorIdAssignerCreator //
	) {
		return new MultiThreadPoolExecutorImpl<>(corePoolSize, maximumPoolSize, secondaryCorePoolSize, maximumSecondaryPoolSize, keepAliveTime, unit,
				workQueue, secondaryWorkQueue, threadFactory, handler, executorIdAssignerCreator);
	}

	void setCorePoolSize(int corePoolSize);

	void setMaximumPoolSize(int maxPoolSize);

	void setSecondaryCorePoolSize(int corePoolSize);

	void setMaximumSecondaryPoolSize(int maxPoolSize);

	void setKeepAliveTime(long time, TimeUnit unit);

	void allowCoreThreadTimeOut(boolean value);

	int prestartAllCoreThreads();

	int getPoolSize();

	BlockingQueue<Runnable> getQueue();

	BlockingQueue<Runnable> getSecondaryQueue();

	int getActiveCount();

	/**
	 * Override the default executor for state change listeners which runs them synchronously as part of the main
	 * operations. This may be used to move to an asynchronous processing of change listeners.
	 *
	 * @param executor
	 *            The executor.
	 */
	void setStateChangeListenerExecutor(Executor executor);

	/**
	 * Register a state change listener. Note that state change listeners are by default executed synchronously during
	 * operations and should therefore finish very fast.
	 * To override this default behaviour see {@link #setStateChangeListenerExecutor(Executor)}.
	 *
	 * @param stateChangeListener
	 *            The state change listener.
	 */
	void registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener);

	/**
	 * Override the default executor for metrics change listeners which runs them synchronously as part of the main
	 * operations. This may be used to move to an asynchronous processing of change listeners.
	 *
	 * @param executor
	 *            The executor.
	 */
	void setMetricsChangeListenerExecutor(Executor executor);

	/**
	 * Register a metrics change listener. Note that state change listeners are by default executed synchronously during
	 * operations and should therefore finish very fast.
	 * To override this default behaviour see {@link #setMetricsChangeListenerExecutor(Executor)}.
	 *
	 * @param stateChangeListener
	 *            The state change listener.
	 */
	void registerMetricsChangeListener(StateChangeListener<TaskRegistryMetrics> stateChangeListener);

	/**
	 * Get a snapshot of the current metrics.
	 *
	 * @return The metrics snapshot.
	 */
	TaskRegistryMetrics getMetricsSnapshot();

	/**
	 * Get a snapshot of the current state.
	 *
	 * @return The state snapshot.
	 */
	TaskRegistryState getStateSnapshot();
}
