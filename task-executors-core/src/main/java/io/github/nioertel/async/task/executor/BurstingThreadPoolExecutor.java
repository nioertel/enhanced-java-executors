package io.github.nioertel.async.task.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface BurstingThreadPoolExecutor extends MultiThreadPoolExecutor {

	/**
	 * Constructor. Create a new bursting thread pool executor. For the details on all parameters see
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
	 * @param burstCorePoolSize
	 *            The core pool size of the burst execution pool.
	 * @param burstMaximumPoolSize
	 *            The maximum pool size of the main pool.
	 * @param keepAliveTime
	 *            The keep alive time.
	 * @param unit
	 *            The unit of the keep alive time
	 * @param workQueue
	 *            The work queue.
	 * @param burstWorkQueue
	 *            The work queue of the burst execution pool.
	 * @param threadFactory
	 *            The thread factory.
	 * @param handler
	 *            The rejected execution handler.
	 */
	static BurstingThreadPoolExecutor newBurstingThreadPoolExecutor(//
			int corePoolSize, //
			int maximumPoolSize, //
			int burstCorePoolSize, //
			int burstMaximumPoolSize, //
			long keepAliveTime, //
			TimeUnit unit, //
			BlockingQueue<Runnable> workQueue, //
			BlockingQueue<Runnable> burstWorkQueue, //
			ThreadFactory threadFactory, //
			RejectedExecutionHandler handler //
	) {
		return new BurstingThreadPoolExecutorImpl(corePoolSize, maximumPoolSize, burstCorePoolSize, burstMaximumPoolSize, keepAliveTime, unit,
				workQueue, burstWorkQueue, threadFactory, handler);
	}

	/**
	 * This parameter decides how many task families can be run on the burst executor in parallel. If a new task is about to
	 * be submitted to the burst executor and the number of currently executing task families on the burst executor is not
	 * smaller than the configured MDOP, the task will instead be parked and submitted later.
	 *
	 * @param mdop
	 *            The maximum degree of parallelism.
	 */
	void setBurstExecutionMDOP(int mdop);

}
