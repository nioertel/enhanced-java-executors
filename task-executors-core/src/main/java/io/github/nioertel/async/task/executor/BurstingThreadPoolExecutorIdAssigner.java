package io.github.nioertel.async.task.executor;

import java.util.Set;

import io.github.nioertel.async.task.registry.ExecutorIdAssigner;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment;
import io.github.nioertel.async.task.registry.ExecutorIdAssignment.ExecutorIdAssignmentCommand;
import io.github.nioertel.async.task.registry.TaskRegistryInfoAccessor;
import io.github.nioertel.async.task.registry.TaskRegistryState;
import io.github.nioertel.async.task.registry.TaskState;

class BurstingThreadPoolExecutorIdAssigner implements ExecutorIdAssigner {

	private static final ExecutorIdAssignment NO_EXECUTOR_ASSIGNMENT = new ExecutorIdAssignment(ExecutorIdAssignmentCommand.PARK, -2L);

	private static final long MAIN_EXECUTOR_ID = MultiThreadPoolExecutorImpl.MAIN_EXECUTOR_ID;

	private static final ExecutorIdAssignment MAIN_EXECUTOR_ASSIGNMENT =
			new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, MAIN_EXECUTOR_ID);

	private static final long BURST_EXECUTOR_ID = MultiThreadPoolExecutorImpl.SECONDARY_EXECUTOR_ID;

	private static final ExecutorIdAssignment BURST_EXECUTOR_ASSIGNMENT =
			new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, BURST_EXECUTOR_ID);

	private final MultiExecutorState executorState;

	private int burstExecutionMDOP;

	public BurstingThreadPoolExecutorIdAssigner(MultiExecutorState executorState, int burstExecutionMDOP) {
		this.executorState = executorState;
		this.burstExecutionMDOP = burstExecutionMDOP;
	}

	public void setBurstExecutionMDOP(int burstExecutionMDOP) {
		this.burstExecutionMDOP = burstExecutionMDOP;
	}

	/**
	 * Decide on which executor the given task should be submitted.
	 *
	 * @param registryInfoAccessor
	 *            The task registry info accessor.
	 * @param taskDetails
	 *            The task details.
	 *
	 * @return The executor assignment specifying which executor will receive the task (see {@link #MAIN_EXECUTOR_ID},
	 *         {@link #BURST_EXECUTOR_ID}).
	 */
	@Override
	public ExecutorIdAssignment assignExecutorId(TaskRegistryInfoAccessor registryInfoAccessor, TaskState taskDetails) {
		if (!executorState.isActive(BURST_EXECUTOR_ID)) {
			// bursting is disabled
			return MAIN_EXECUTOR_ASSIGNMENT;
		} else if (0L == taskDetails.getStackLevel()) {
			// level 0 always goes to main executor
			return MAIN_EXECUTOR_ASSIGNMENT;
		} else if (BURST_EXECUTOR_ID == taskDetails.getParentTaskAssignedExecutorId()) {
			// parent is running on burst executor -> child should also run on burst executor
			return BURST_EXECUTOR_ASSIGNMENT;
		} else {
			TaskRegistryState registryState = registryInfoAccessor.getStateSnapshot();
			Set<Long> currentlyExecutingTasks = registryState.getCurrentlyExecutingTasks();
			if (currentlyExecutingTasks.size() < executorState.getMaximumPoolSize(MAIN_EXECUTOR_ID)) {
				// there is still space in the main pool -> task can be directly submitted there
				return MAIN_EXECUTOR_ASSIGNMENT;
			} else if (!currentlyExecutingTasks.contains(taskDetails.getParentTaskId())) {
				// main pool is full but parent task has no dependency on submitted task -> task can wait for execution on main pool
				return MAIN_EXECUTOR_ASSIGNMENT;
			} else {
				// main pool is full and we may be in a deadlock situation -> execute task on burst pool
				if (isBurstCapacityAvailable(taskDetails.getTaskFamilyId(), registryState)) {
					return BURST_EXECUTOR_ASSIGNMENT;
				} else {
					return NO_EXECUTOR_ASSIGNMENT;
				}
			}
		}
	}

	private boolean isBurstCapacityAvailable(long taskFamily, TaskRegistryState registryState) {
		Set<Long> currentlyExecutingTaskFamilies = registryState.getCurrentlyAssignedTasksByTaskFamilyForExecutor(BURST_EXECUTOR_ID).keySet();
		return currentlyExecutingTaskFamilies.contains(taskFamily) || currentlyExecutingTaskFamilies.size() < burstExecutionMDOP;
	}
}