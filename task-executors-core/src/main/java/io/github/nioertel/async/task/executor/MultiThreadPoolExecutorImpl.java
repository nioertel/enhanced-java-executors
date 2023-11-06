package io.github.nioertel.async.task.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.Identifiable;
import io.github.nioertel.async.task.registry.IdentifiableRunnable;
import io.github.nioertel.async.task.registry.TaskRegistryInfoAccessor;
import io.github.nioertel.async.task.registry.TaskRegistryMetrics;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;
import io.github.nioertel.async.task.registry.internal.TaskExecutorAssignmentState;
import io.github.nioertel.async.task.registry.internal.TaskRegistry;
import io.github.nioertel.async.task.registry.internal.ThreadTrackingTaskDecorator;
import io.github.nioertel.async.task.registry.state.StateChangeListener;

class MultiThreadPoolExecutorImpl<T extends ExecutorIdAssigner> extends AbstractExecutorService implements MultiThreadPoolExecutor {

	public static final long MAIN_EXECUTOR_ID = 0L;

	public static final long SECONDARY_EXECUTOR_ID = 1L;

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final TaskRegistry taskRegistry;

	private final ThreadTrackingTaskDecorator trackingTaskDecorator;

	private final java.util.concurrent.ThreadPoolExecutor mainThreadPoolExecutor;

	private final java.util.concurrent.ThreadPoolExecutor secondaryThreadPoolExecutor;

	private final MultiExecutorState executorState;

	private final T executorIdAssigner;

	private final Map<Long, Runnable> parkedDecoratedTasks = new ConcurrentHashMap<>();

	private boolean secondaryPoolEnabled;

	/**
	 * Constructor. For the details on all parameters see {@link java.util.concurrent.ThreadPoolExecutor}.
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
	public MultiThreadPoolExecutorImpl(//
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
		if (0 == maximumSecondaryPoolSize) {
			this.secondaryPoolEnabled = false;
			maximumSecondaryPoolSize = 1; // required by ThreadPooolExecutor
		} else {
			this.secondaryPoolEnabled = true;
		}
		this.mainThreadPoolExecutor =
				new java.util.concurrent.ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		this.secondaryThreadPoolExecutor = new java.util.concurrent.ThreadPoolExecutor(secondaryCorePoolSize, maximumSecondaryPoolSize, keepAliveTime,
				unit, secondaryWorkQueue, threadFactory, handler);
		this.executorState = new MultiExecutorStateImpl(//
				Map.of(//
						0L, new ExecutorStateView(mainThreadPoolExecutor, () -> true), //
						1L, new ExecutorStateView(secondaryThreadPoolExecutor, () -> secondaryPoolEnabled)//
				)//
		);
		this.executorIdAssigner = executorIdAssignerCreator.apply(executorState);
		this.taskRegistry = TaskRegistry.newTaskRegistry(executorIdAssigner);
		this.trackingTaskDecorator = taskRegistry.getTrackingTaskDecorator();
	}

	@Override
	public void execute(Runnable command) {
		Identifiable decoratedTask = tryExtractPreDecoratedTask(command);
		if (null == decoratedTask) {
			// task has not been decorated yet -> decorate and then use it
			IdentifiableRunnable wrappedCommand = trackingTaskDecorator.decorate(command);
			decoratedTask = wrappedCommand;
			command = wrappedCommand;
		}
		command = addResubmissionTrigger(command);

		TaskState submissionResult = taskRegistry.taskSubmitted(decoratedTask, Thread.currentThread());
		submitTaskToExecutor(command, submissionResult);
	}

	private Runnable addResubmissionTrigger(Runnable command) {
		return () -> {
			try {
				command.run();
			} finally {
				if (!parkedDecoratedTasks.isEmpty()) {
					resubmitParkedTasks();
				}
			}
		};
	}

	private void resubmitParkedTasks() {
		for (TaskState submissionResult : taskRegistry.resubmitParkedTasks()) {
			Runnable taskForResubmission = parkedDecoratedTasks.remove(submissionResult.getId());
			if (null == taskForResubmission) {
				logger.warn("Skipping resubmission of task {} as task does not exist.", submissionResult.getId());
				// TODO: cleanup registry state
			} else {
				// TODO: This can likely be further optimised (currently we run into situations where this is called multiple times in
				// parallel - which is technically ok but costs performance)
				submitTaskToExecutor(taskForResubmission, submissionResult);
			}
		}
	}

	private void submitTaskToExecutor(Runnable command, TaskState submissionResult) {
		if (TaskExecutorAssignmentState.PARKED == submissionResult.getExecutorAssignmentState()) {
			logger.error("Task {} has been temporarily parked and will be resubmitted later.", submissionResult.getId());
			parkedDecoratedTasks.put(submissionResult.getId(), command);
			return;
		} else if (MAIN_EXECUTOR_ID == submissionResult.getAssignedExecutorId()) {
			logger.debug("Running task {} on main executor.", submissionResult.getId());
			mainThreadPoolExecutor.execute(command);
		} else if (!secondaryPoolEnabled) {
			logger.error("Cannot run task {} on secondary executor when secondary pool is set to maximum size 0.", submissionResult.getId());
			throw new IllegalStateException("Secondary pool has been scaled to 0. Cannot run task.");
		} else {
			logger.debug("Running task {} on secondary executor.", submissionResult.getId());
			secondaryThreadPoolExecutor.execute(command);
		}
	}

	private IdentifiableRunnable tryExtractPreDecoratedTask(Runnable command) {
		if (command instanceof IdentifiableRunnable) {
			return (IdentifiableRunnable) command;
		} else {
			return null;
		}
	}

	protected T getExecutorIdAssigner() {
		return executorIdAssigner;
	}

	@Override
	protected <R> RunnableFuture<R> newTaskFor(Runnable runnable, R value) {
		return new IdentifiableFutureTask<>(trackingTaskDecorator.decorate(runnable), value);
	}

	@Override
	protected <R> RunnableFuture<R> newTaskFor(Callable<R> callable) {
		return new IdentifiableFutureTask<>(trackingTaskDecorator.decorate(callable));
	}

	@Override
	public void shutdown() {
		mainThreadPoolExecutor.shutdown();
		secondaryThreadPoolExecutor.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		List<Runnable> remainingQueuedTasks = new ArrayList<>();
		remainingQueuedTasks.addAll(mainThreadPoolExecutor.shutdownNow());
		remainingQueuedTasks.addAll(secondaryThreadPoolExecutor.shutdownNow());
		return remainingQueuedTasks;
	}

	@Override
	public boolean isShutdown() {
		return mainThreadPoolExecutor.isShutdown() && secondaryThreadPoolExecutor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return mainThreadPoolExecutor.isTerminated() && secondaryThreadPoolExecutor.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		long startTimeMillis = System.currentTimeMillis();
		if (!mainThreadPoolExecutor.awaitTermination(timeout, unit)) {
			return false;
		} else {
			long remainingMillisToWait = System.currentTimeMillis() - startTimeMillis - TimeUnit.MILLISECONDS.convert(timeout, unit);
			if (remainingMillisToWait <= 0) {
				return false;
			} else {
				return secondaryThreadPoolExecutor.awaitTermination(remainingMillisToWait, TimeUnit.MILLISECONDS);
			}
		}
	}

	@Override
	public void setCorePoolSize(int corePoolSize) {
		mainThreadPoolExecutor.setCorePoolSize(corePoolSize);
	}

	@Override
	public void setMaximumPoolSize(int maxPoolSize) {
		mainThreadPoolExecutor.setMaximumPoolSize(maxPoolSize);
	}

	@Override
	public void setSecondaryCorePoolSize(int corePoolSize) {
		secondaryThreadPoolExecutor.setCorePoolSize(corePoolSize);
	}

	@Override
	public void setMaximumSecondaryPoolSize(int maxPoolSize) {
		if (0 == maxPoolSize) {
			secondaryThreadPoolExecutor.setMaximumPoolSize(1);
			secondaryPoolEnabled = false;
		} else {
			secondaryThreadPoolExecutor.setMaximumPoolSize(maxPoolSize);
			secondaryPoolEnabled = true;
		}
	}

	@Override
	public void setKeepAliveTime(long time, TimeUnit unit) {
		mainThreadPoolExecutor.setKeepAliveTime(time, unit);
		secondaryThreadPoolExecutor.setKeepAliveTime(time, unit);
	}

	@Override
	public void allowCoreThreadTimeOut(boolean value) {
		mainThreadPoolExecutor.allowCoreThreadTimeOut(value);
		secondaryThreadPoolExecutor.allowCoreThreadTimeOut(value);
	}

	@Override
	public int prestartAllCoreThreads() {
		return mainThreadPoolExecutor.prestartAllCoreThreads() + secondaryThreadPoolExecutor.prestartAllCoreThreads();
	}

	@Override
	public int getPoolSize() {
		return mainThreadPoolExecutor.getPoolSize();
	}

	@Override
	public BlockingQueue<Runnable> getQueue() {
		return mainThreadPoolExecutor.getQueue();
	}

	@Override
	public BlockingQueue<Runnable> getSecondaryQueue() {
		return secondaryThreadPoolExecutor.getQueue();
	}

	@Override
	public int getActiveCount() {
		return mainThreadPoolExecutor.getActiveCount() + secondaryThreadPoolExecutor.getActiveCount();
	}

	@Override
	public void setStateChangeListenerExecutor(Executor executor) {
		taskRegistry.setStateChangeListenerExecutor(executor);
	}

	@Override
	public void registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener) {
		taskRegistry.registerStateChangeListener(stateChangeListener);
	}

	@Override
	public TaskRegistryMetrics getMetricsSnapshot() {
		return taskRegistry.getMetricsSnapshot();
	}

	@Override
	public TaskRegistryState getStateSnapshot() {
		return taskRegistry.getStateSnapshot();
	}

	protected TaskRegistryInfoAccessor getTaskRegistryInfo() {
		return taskRegistry;
	}
}
