package io.github.nioertel.async.task.executor;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

class BurstingThreadPoolExecutorImpl extends MultiThreadPoolExecutorImpl<BurstingThreadPoolExecutorIdAssigner> implements BurstingThreadPoolExecutor {

	public static final int DEFAULT_BURST_EXECUTION_MDOP = 1;

	/**
	 * Constructor. For the details on all parameters see {@link java.util.concurrent.ThreadPoolExecutor}.
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
	public BurstingThreadPoolExecutorImpl(//
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
		super(corePoolSize, maximumPoolSize, burstCorePoolSize, burstMaximumPoolSize, keepAliveTime, unit, workQueue, burstWorkQueue, threadFactory,
				handler, executorState -> new BurstingThreadPoolExecutorIdAssigner(executorState, DEFAULT_BURST_EXECUTION_MDOP));
	}

	@Override
	public void setBurstExecutionMDOP(int burstExecutionMDOP) {
		getExecutorIdAssigner().setBurstExecutionMDOP(burstExecutionMDOP);
	}

}
