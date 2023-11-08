package io.github.nioertel.async.task.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import io.github.nioertel.async.task.registry.ExecutorIdAssignment.ExecutorIdAssignmentCommand;
import io.github.nioertel.async.task.registry.internal.TaskProgress;
import io.github.nioertel.async.task.registry.internal.TaskRegistry;
import io.github.nioertel.async.task.registry.listeners.LoggingMetricsStateChangeListener;
import io.github.nioertel.async.task.registry.listeners.LoggingTaskRegistryStateChangeListener;
import io.github.nioertel.async.test.ControllableTestTask;
import io.github.nioertel.async.test.SimpleTestTask;

class TaskRegistryTest {

	private static class ControllableExecutorIdAssigner implements ExecutorIdAssigner {

		private Map<Long, Long> executorIdAssignments = new HashMap<>();

		@Override
		public ExecutorIdAssignment assignExecutorId(TaskRegistryInfoAccessor registryInfoAccessor, TaskState taskDetails) {
			Long executorId = executorIdAssignments.get(taskDetails.getId());
			if (null != executorId) {
				return new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, executorId);
			} else {
				return new ExecutorIdAssignment(ExecutorIdAssignmentCommand.PARK, -2L);
			}
		}

		public void addExecutorAssignment(long taskId, long executorId) {
			executorIdAssignments.put(taskId, executorId);
		}

	}

	@Test
	void testTaskTracking() throws InterruptedException {
		ExecutorService testController = Executors.newCachedThreadPool();

		TaskRegistry taskRegistry =
				TaskRegistry.newTaskRegistry((registryInfoAccessor, taskDetals) -> new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, 0L));
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		ControllableTestTask task1Internal = new ControllableTestTask("Test task 1");
		IdentifiableRunnable task1 = taskRegistry.getTrackingTaskDecorator().decorate(task1Internal);

		ControllableTestTask task2Internal = new ControllableTestTask("Test task 2");
		IdentifiableRunnable task2 = taskRegistry.getTrackingTaskDecorator().decorate(task2Internal);

		// before submitting any tasks, the collection of running tasks should be empty
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();

		// submit both tasks
		taskRegistry.taskSubmitted(task1, Thread.currentThread());
		testController.submit(task1);
		taskRegistry.taskSubmitted(task2, Thread.currentThread());
		testController.submit(task2);

		// wait until both tasks have entered their run methods
		task1Internal.waitUntilStarted();
		task2Internal.waitUntilStarted();
		// both tasks should now appear in registry
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyExecutingTasks()).containsExactlyInAnyOrder(task1.getId(),
				task2.getId());

		// let both tasks enter the next stage of their processing
		task1Internal.allowStart();
		task1Internal.waitUntilRunning();
		task2Internal.allowStart();
		task2Internal.waitUntilRunning();
		// both tasks should still appear in registry
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyExecutingTasks()).containsExactlyInAnyOrder(task1.getId(),
				task2.getId());

		// let task 1 end its processing
		task1Internal.allowFinish();
		task1Internal.waitUntilReadyToFinish();
		task1Internal.waitUntilFinished();
		// wait for grace period
		TimeUnit.MILLISECONDS.sleep(50L);
		// task 1 should not appear in registry anymore
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyExecutingTasks()).containsExactlyInAnyOrder(task2.getId());

		// let task 2 end its processing
		task2Internal.allowFinish();
		task2Internal.waitUntilReadyToFinish();
		task2Internal.waitUntilFinished();
		// wait for grace period
		TimeUnit.MILLISECONDS.sleep(50L);
		// task 2 should not appear in registry anymore
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyExecutingTasks()).isEmpty();
	}

	@Test
	void testTaskHierarchyTracking() {
		TaskRegistry taskRegistry =
				TaskRegistry.newTaskRegistry((registryInfoAccessor, taskDetals) -> new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, 0L));
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		Thread mainThread = Thread.currentThread();

		SimpleTestTask task1Internal = new SimpleTestTask("Task 1");
		IdentifiableRunnable task1 = taskRegistry.getTrackingTaskDecorator().decorate(task1Internal);
		Thread task1Thread = new Thread();

		SimpleTestTask task2Internal = new SimpleTestTask("Task 2");
		IdentifiableRunnable task2 = taskRegistry.getTrackingTaskDecorator().decorate(task2Internal);
		Thread task2Thread = new Thread();

		SimpleTestTask task3Internal = new SimpleTestTask("Task 3");
		IdentifiableRunnable task3 = taskRegistry.getTrackingTaskDecorator().decorate(task3Internal);
		Thread task3Thread = new Thread();

		SimpleTestTask task4Internal = new SimpleTestTask("Task 4");
		IdentifiableRunnable task4 = taskRegistry.getTrackingTaskDecorator().decorate(task4Internal);
		Thread task4Thread = new Thread();

		SimpleTestTask task5Internal = new SimpleTestTask("Task 5");
		IdentifiableRunnable task5 = taskRegistry.getTrackingTaskDecorator().decorate(task5Internal);
		Thread task5Thread = new Thread();

		TaskState submissionResult = taskRegistry.taskSubmitted(task1, mainThread);
		taskRegistry.taskExecutionStarted(task1, task1Thread);
		BDDAssertions.assertThat(submissionResult.getParentTaskId()).isEqualTo(-1L);
		BDDAssertions.assertThat(submissionResult.getStackLevel()).isEqualTo(0L);

		submissionResult = taskRegistry.taskSubmitted(task2, mainThread);
		taskRegistry.taskExecutionStarted(task2, task2Thread);
		BDDAssertions.assertThat(submissionResult.getParentTaskId()).isEqualTo(-1L);
		BDDAssertions.assertThat(submissionResult.getStackLevel()).isEqualTo(0L);

		submissionResult = taskRegistry.taskSubmitted(task3, task1Thread);
		taskRegistry.taskExecutionStarted(task3, task3Thread);
		BDDAssertions.assertThat(submissionResult.getParentTaskId()).isEqualTo(task1.getId());
		BDDAssertions.assertThat(submissionResult.getStackLevel()).isEqualTo(1L);

		submissionResult = taskRegistry.taskSubmitted(task4, task2Thread);
		taskRegistry.taskExecutionStarted(task4, task4Thread);
		BDDAssertions.assertThat(submissionResult.getParentTaskId()).isEqualTo(task2.getId());
		BDDAssertions.assertThat(submissionResult.getStackLevel()).isEqualTo(1L);

		submissionResult = taskRegistry.taskSubmitted(task5, task4Thread);
		taskRegistry.taskExecutionStarted(task5, task5Thread);
		BDDAssertions.assertThat(submissionResult.getParentTaskId()).isEqualTo(task4.getId());
		BDDAssertions.assertThat(submissionResult.getStackLevel()).isEqualTo(2L);

		TaskRegistryMetrics metrics = taskRegistry.getMetricsSnapshot();
		BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(5L);
		BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(5L);
		BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(5L);

		taskRegistry.taskExecutionFinished(task5);
		taskRegistry.taskExecutionFinished(task4);
		taskRegistry.taskExecutionFinished(task3);
		taskRegistry.taskExecutionFinished(task2);
		taskRegistry.taskExecutionFinished(task1);

		metrics = taskRegistry.getMetricsSnapshot();
		BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(5L);
		BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(5L);
	}

	@Test
	void testTaskResubmission() {
		ControllableExecutorIdAssigner executorIdAssigner = new ControllableExecutorIdAssigner();
		TaskRegistry taskRegistry = TaskRegistry.newTaskRegistry(executorIdAssigner);
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		Thread mainThread = Thread.currentThread();

		SimpleTestTask task1Internal = new SimpleTestTask("Task 1");
		IdentifiableRunnable task1 = taskRegistry.getTrackingTaskDecorator().decorate(task1Internal);
		Thread task1Thread = new Thread();

		// submit task -> should be parked
		TaskState taskState = taskRegistry.taskSubmitted(task1, mainThread);
		BDDAssertions.assertThat(taskState.getTaskProgress()).isSameAs(TaskProgress.PARKED);
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyParkedTasks()).containsExactly(task1.getId());
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getNumCurrentlyParkedTasks()).isEqualTo(1L);
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getTotalNumSubmittedTasks()).isEqualTo(1L);

		// resubmit task -> should stay parked
		List<TaskState> taskStates = taskRegistry.resubmitParkedTasks();
		BDDAssertions.assertThat(taskStates).hasSize(0);
		taskState = taskRegistry.getStateSnapshot().getTaskState(task1.getId());
		BDDAssertions.assertThat(taskState.getTaskProgress()).isSameAs(TaskProgress.PARKED);
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyParkedTasks()).containsExactly(task1.getId());
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getNumCurrentlyParkedTasks()).isEqualTo(1L);
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getTotalNumSubmittedTasks()).isEqualTo(1L);

		// reconfigure executor id assignments and resubmit task -> should now be assigned
		executorIdAssigner.addExecutorAssignment(task1.getId(), 1L);
		taskStates = taskRegistry.resubmitParkedTasks();
		BDDAssertions.assertThat(taskStates).hasSize(1);
		taskState = taskStates.get(0);
		BDDAssertions.assertThat(taskState.getTaskProgress()).isSameAs(TaskProgress.SUBMITTED);
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot().getCurrentlyParkedTasks()).isEmpty();
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getNumCurrentlyParkedTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot().getTotalNumSubmittedTasks()).isEqualTo(1L);

		// start + finish task execution -> should run normally
		taskRegistry.taskExecutionStarted(task1, task1Thread);
		taskRegistry.taskExecutionFinished(task1);

		TaskRegistryMetrics metrics = taskRegistry.getMetricsSnapshot();
		BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(1L);
		BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
		BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
	}

	@Test
	void testDiscardUnknownTask() {
		ControllableExecutorIdAssigner executorIdAssigner = new ControllableExecutorIdAssigner();
		TaskRegistry taskRegistry = TaskRegistry.newTaskRegistry(executorIdAssigner);
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		TaskState taskState = taskRegistry.taskDiscarded(0L);
		BDDAssertions.assertThat(taskState).isNull();
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(0L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(0L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});
	}

	@Test
	void testDiscardPendingTask() {
		TaskRegistry taskRegistry = TaskRegistry.newTaskRegistry((r, t) -> new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, 1L));
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		IdentifiableRunnable task = taskRegistry.getTrackingTaskDecorator().decorate(() -> {
		});
		Thread taskSubmissionThread = Thread.currentThread();

		// submit task
		TaskState taskState1 = taskRegistry.taskSubmitted(task, taskSubmissionThread);
		BDDAssertions.assertThat(taskState1).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.SUBMITTED);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs())
					.isEqualTo(taskState1.getExecutorAssignedDateEpochMillis() - taskState1.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});

		// discard submitted task
		TaskState taskState2 = taskRegistry.taskDiscarded(task.getId());
		BDDAssertions.assertThat(taskState2).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.DISCARDED);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(3L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs())
					.isEqualTo(taskState1.getExecutorAssignedDateEpochMillis() - taskState1.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});
	}

	@Test
	void testDiscardParkedTask() {
		TaskRegistry taskRegistry = TaskRegistry.newTaskRegistry((r, t) -> new ExecutorIdAssignment(ExecutorIdAssignmentCommand.PARK, 1L));
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		IdentifiableRunnable task = taskRegistry.getTrackingTaskDecorator().decorate(() -> {
		});
		Thread taskSubmissionThread = Thread.currentThread();

		// submit task
		TaskState taskState1 = taskRegistry.taskSubmitted(task, taskSubmissionThread);
		BDDAssertions.assertThat(taskState1).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.PARKED);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).containsExactly(task.getId());
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});

		// discard parked task
		TaskState taskState2 = taskRegistry.taskDiscarded(task.getId());
		BDDAssertions.assertThat(taskState2).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.DISCARDED_WHILE_PARKED);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(3L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});
	}

	@Test
	void testDiscardRunningTask() {
		TaskRegistry taskRegistry = TaskRegistry.newTaskRegistry((r, t) -> new ExecutorIdAssignment(ExecutorIdAssignmentCommand.ASSIGN, 1L));
		taskRegistry.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
		taskRegistry.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

		IdentifiableRunnable task = taskRegistry.getTrackingTaskDecorator().decorate(() -> {
		});
		Thread taskSubmissionThread = Thread.currentThread();
		Thread taskExecutionThread = new Thread();

		// submit task
		TaskState taskState1 = taskRegistry.taskSubmitted(task, taskSubmissionThread);
		BDDAssertions.assertThat(taskState1).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.SUBMITTED);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs())
					.isEqualTo(taskState1.getExecutorAssignedDateEpochMillis() - taskState1.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});

		// mark task as running
		taskRegistry.taskExecutionStarted(task, taskExecutionThread);
		TaskState taskState2 = taskRegistry.getTaskStateSnapshot(task.getId());
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(3L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).containsExactly(taskState1.getId());
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).hasSize(1);
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(2L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(01);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs())
					.isEqualTo(taskState2.getExecutorAssignedDateEpochMillis() - taskState2.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs())
					.isEqualTo(taskState2.getExecutionStartDateEpochMillis() - taskState2.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});

		// discard running task
		TaskState taskState3 = taskRegistry.taskDiscarded(task.getId());
		BDDAssertions.assertThat(taskState3).satisfies(state -> {
			BDDAssertions.assertThat(state).isNotNull();
			BDDAssertions.assertThat(state.getTaskProgress()).isSameAs(TaskProgress.DISCARDED_WHILE_RUNNING);
		});
		BDDAssertions.assertThat(taskRegistry.getStateSnapshot()).satisfies(state -> {
			BDDAssertions.assertThat(state.getStateVersion()).isEqualTo(4L);
			BDDAssertions.assertThat(state.getLastUsedTaskId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getLastUsedTaskFamilyId()).isEqualTo(1L);
			BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
			BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
		});
		BDDAssertions.assertThat(taskRegistry.getMetricsSnapshot()).satisfies(metrics -> {
			BDDAssertions.assertThat(metrics.getStateVersion()).isEqualTo(3L);
			BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getNumCurrentlyParkedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(0L);
			BDDAssertions.assertThat(metrics.getTotalNumDiscardedTasks()).isEqualTo(1L);
			BDDAssertions.assertThat(metrics.getTotalExecutorAssignmentWaitTimeMs())
					.isEqualTo(taskState1.getExecutorAssignedDateEpochMillis() - taskState1.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalWaitTimeForExecutionStartMs())
			.isEqualTo(taskState3.getExecutionStartDateEpochMillis() - taskState3.getSubmissionDateEpochMillis());
			BDDAssertions.assertThat(metrics.getTotalExecutionTimeMs()).isEqualTo(0L);
		});
	}
	// TODO: Add test for (parent) executor assignment
}
