package io.github.nioertel.async.task.executor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import io.github.nioertel.async.task.actuator.TaskRegistryInsightsRetriever;
import io.github.nioertel.async.task.actuator.TaskRegistryStateEndpoint;
import io.github.nioertel.async.task.actuator.TaskRegistryStateEndpoint.TaskRegistryStateSummaryOverall;
import io.github.nioertel.async.task.executor.test.TestApp;
import io.github.nioertel.async.task.registry.internal.TaskProgress;
import io.github.nioertel.async.test.NamedThreadFactory;
import io.github.nioertel.async.test.ThrowingSupplier;

@SpringBootTest(//
		classes = TestApp.class, //
		webEnvironment = WebEnvironment.RANDOM_PORT, //
		properties = { //
				"logging.level.io=DEBUG", //
				"management.endpoints.web.exposure.include=*", //
				"spring.jackson.serialization.indent_output=true" //
		})
@Import(TaskExecutorStateEndpointTest.Beans.class)
class TaskExecutorStateEndpointTest {

	static class Beans {

		private static final String TASK_EXECUTOR_NAME = "test-1";

		@Bean
		TaskRegistryInsightsRetriever taskRegistryInsightsRetrieverTest1(RegistryBackedExecutorService executor) {
			return new TaskRegistryInsightsRetriever(//
					TASK_EXECUTOR_NAME, // registryName
					executor::getStateSnapshot, // stateSupplier
					executor::getMetricsSnapshot, // metricsSupplier
					100// micrometerMetricsChangePublishingIntervalMillis
			);
		}

		@Bean
		BurstingThreadPoolExecutor executorService() {
			return BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
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
		}

	}

	@Autowired
	private TaskRegistryStateEndpoint taskRegistryStateEndpoint;

	@Autowired
	private ObjectMapper om;

	@Autowired
	private BurstingThreadPoolExecutor executorService;

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void actuatorShouldBePresent() {
		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class, Map.of());
		BDDAssertions.assertThat(health.getStatusCode()).isSameAs(HttpStatus.OK);
		BDDAssertions.assertThat(JsonPath.<String>read(health.getBody(), "status")).isEqualTo("UP");
	}

	@Test
	void actuatorShouldPublishTaskRegistryStateEndpoint() {
		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/taskRegistryState", String.class, Map.of());
		System.out.println(health);
		BDDAssertions.assertThat(health.getStatusCode()).isSameAs(HttpStatus.OK);
		BDDAssertions.assertThat((Map<?, ?>) JsonPath.parse(health.getBody()).json())//
				.hasSize(2)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo("snapshotTime");
				})//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo("stateByTaskRegistry");
				});
	}

	@Test
	void runningTaskShouldAppearInTaskRegistryStateViaActuator() throws JsonProcessingException {
		runningTaskShouldAppearInTaskRegistryState(() -> {
			ResponseEntity<String> health = restTemplate.getForEntity("/actuator/taskRegistryState", String.class, Map.of());
			System.out.println(health);
			BDDAssertions.assertThat(health.getStatusCode()).isSameAs(HttpStatus.OK);
			return om.readValue(health.getBody(), TaskRegistryStateSummaryOverall.class);
		});
	}

	@Test
	void runningTaskShouldAppearInTaskRegistryStateViaEndpointBean() throws JsonProcessingException {
		runningTaskShouldAppearInTaskRegistryState(() -> {
			return taskRegistryStateEndpoint.state();
		});
	}

	private void runningTaskShouldAppearInTaskRegistryState(
			ThrowingSupplier<TaskRegistryStateSummaryOverall, JsonProcessingException> taskRegistryStateRetriever) throws JsonProcessingException {
		TestRunnable task = new TestRunnable();
		try {
			runningTaskShouldAppearInTaskRegistryState(task, taskRegistryStateRetriever);
		} finally {
			task.reset();
		}
	}

	private void runningTaskShouldAppearInTaskRegistryState(TestRunnable task,
			ThrowingSupplier<TaskRegistryStateSummaryOverall, JsonProcessingException> taskRegistryStateRetriever) throws JsonProcessingException {
		TaskRegistryStateSummaryOverall allStatesBeforeTest = taskRegistryStateRetriever.get();
		BDDAssertions.assertThat(allStatesBeforeTest.getStateByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
					BDDAssertions.assertThat(v).satisfies(state -> {
						BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
						BDDAssertions.assertThat(state.getThreadIdTaskIdMappings()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
					});
				});

		executorService.submit(task);
		BDDAssertions.assertThat(task.blockUntilCoreLogicFinished(30, TimeUnit.SECONDS))
				.describedAs("Expecting task to start within 30 seconds. Issue with test setup.").isTrue();

		TaskRegistryStateSummaryOverall registryStateOverall = taskRegistryStateRetriever.get();
		BDDAssertions.assertThat(registryStateOverall.getStateByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
					BDDAssertions.assertThat(v).satisfies(state -> {
						BDDAssertions.assertThat(state.getCurrentlySubmittedTasks())//
								.hasSize(1)//
								.allSatisfy((taskId, taskDetails) -> {
									BDDAssertions.assertThat(taskDetails.getTaskId()).isEqualTo(taskId);
									BDDAssertions.assertThat(taskDetails.getStackLevel()).isEqualTo(0L);
									BDDAssertions.assertThat(taskDetails.getTaskProgress()).isSameAs(TaskProgress.RUNNING);

									BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).containsExactly(taskId);
									BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily())//
											.containsEntry(0L, Map.of(taskDetails.getTaskFamilyId(), Set.of(taskDetails.getTaskId())));
									BDDAssertions.assertThat(state.getThreadIdTaskIdMappings()).containsEntry(taskDetails.getAssignedThreadId(),
											taskId);
								});
						BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
					});
				});

		task.continueRunning();
		BDDAssertions.assertThat(task.blockUntilExecutionFinished(30, TimeUnit.SECONDS))
				.describedAs("Expecting task to start within 30 seconds. Issue with test setup.").isTrue();

		registryStateOverall = taskRegistryStateRetriever.get();
		BDDAssertions.assertThat(registryStateOverall.getStateByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
					BDDAssertions.assertThat(v).satisfies(state -> {
						BDDAssertions.assertThat(state.getCurrentlySubmittedTasks()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyExecutingTasks()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyAssignedTasksByExecutorAndTaskFamily()).isEmpty();
						BDDAssertions.assertThat(state.getThreadIdTaskIdMappings()).isEmpty();
						BDDAssertions.assertThat(state.getCurrentlyParkedTasks()).isEmpty();
					});
				});
	}

}
