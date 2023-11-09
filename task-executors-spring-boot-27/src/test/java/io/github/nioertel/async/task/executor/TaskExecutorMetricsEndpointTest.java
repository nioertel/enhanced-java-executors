package io.github.nioertel.async.task.executor;

import java.util.Map;
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
import io.github.nioertel.async.task.actuator.TaskRegistryMetricsEndpoint;
import io.github.nioertel.async.task.actuator.TaskRegistryMetricsEndpoint.TaskRegistryMetricsSummaryOverall;
import io.github.nioertel.async.task.actuator.TaskRegistryMetricsSummary;
import io.github.nioertel.async.task.executor.test.TestApp;
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
@Import(TaskExecutorMetricsEndpointTest.Beans.class)
class TaskExecutorMetricsEndpointTest {

	static class Beans {

		private static final String TASK_EXECUTOR_NAME = "test-1";

		@Bean
		TaskRegistryInsightsRetriever taskRegistryMetricsRetriever(BurstingThreadPoolExecutor executor) {
			return new TaskRegistryInsightsRetriever(//
					TASK_EXECUTOR_NAME, //
					executor::getStateSnapshot, //
					executor::getMetricsSnapshot//
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
	private TaskRegistryMetricsEndpoint taskRegistryMetricsEndpoint;

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
	void actuatorShouldPublishTaskRegistryMetricsEndpoint() {
		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/taskRegistryMetrics", String.class, Map.of());
		BDDAssertions.assertThat(health.getStatusCode()).isSameAs(HttpStatus.OK);
		BDDAssertions.assertThat((Map<?, ?>) JsonPath.parse(health.getBody()).json())//
				.hasSize(2)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo("snapshotTime");
				})//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo("metricsByTaskRegistry");
				});
	}

	@Test
	void runningTaskShouldAppearInMetricsViaActuator() throws JsonProcessingException {
		runningTaskShouldAppearInMetrics(() -> {
			ResponseEntity<String> health = restTemplate.getForEntity("/actuator/taskRegistryMetrics", String.class, Map.of());
			BDDAssertions.assertThat(health.getStatusCode()).isSameAs(HttpStatus.OK);
			System.out.println(health.getBody());
			return om.readValue(health.getBody(), TaskRegistryMetricsSummaryOverall.class);
		});
	}

	@Test
	void runningTaskShouldAppearInMetricsViaEndpointBean() throws JsonProcessingException {
		runningTaskShouldAppearInMetrics(() -> {
			return taskRegistryMetricsEndpoint.metrics();
		});
	}

	private void runningTaskShouldAppearInMetrics(
			ThrowingSupplier<TaskRegistryMetricsSummaryOverall, JsonProcessingException> executionMetricsRetriever) throws JsonProcessingException {
		TestRunnable task = new TestRunnable();
		try {
			runningTaskShouldAppearInMetrics(task, executionMetricsRetriever);
		} finally {
			task.reset();
		}
	}

	private void runningTaskShouldAppearInMetrics(TestRunnable task,
			ThrowingSupplier<TaskRegistryMetricsSummaryOverall, JsonProcessingException> taskRegistryMetricsRetriever)
			throws JsonProcessingException {
		TaskRegistryMetricsSummaryOverall allMetricsBeforeTest = taskRegistryMetricsRetriever.get();
		BDDAssertions.assertThat(allMetricsBeforeTest.getMetricsByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
				});
		TaskRegistryMetricsSummary metricsSummaryBeforeTest = allMetricsBeforeTest.getMetricsByTaskRegistry().get(Beans.TASK_EXECUTOR_NAME);

		executorService.submit(task);
		BDDAssertions.assertThat(task.blockUntilCoreLogicFinished(30, TimeUnit.SECONDS))
				.describedAs("Expecting task to start within 30 seconds. Issue with test setup.").isTrue();

		TaskRegistryMetricsSummaryOverall executionMetricsOverall = taskRegistryMetricsRetriever.get();
		BDDAssertions.assertThat(executionMetricsOverall.getMetricsByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
					BDDAssertions.assertThat(v).satisfies(metrics -> {
						BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(1L);
						BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(1L);
						BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks())
								.isEqualTo(metricsSummaryBeforeTest.getTotalNumSubmittedTasks() + 1L);
						BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks()).isEqualTo(metricsSummaryBeforeTest.getTotalNumExecutedTasks());
					});
				});

		task.continueRunning();
		BDDAssertions.assertThat(task.blockUntilExecutionFinished(30, TimeUnit.SECONDS))
				.describedAs("Expecting task to start within 30 seconds. Issue with test setup.").isTrue();

		executionMetricsOverall = taskRegistryMetricsRetriever.get();
		BDDAssertions.assertThat(executionMetricsOverall.getMetricsByTaskRegistry())//
				.hasSize(1)//
				.anySatisfy((k, v) -> {
					BDDAssertions.assertThat(k).isEqualTo(Beans.TASK_EXECUTOR_NAME);
					BDDAssertions.assertThat(v).satisfies(metrics -> {
						BDDAssertions.assertThat(metrics.getNumCurrentlyExecutingTasks()).isEqualTo(0L);
						BDDAssertions.assertThat(metrics.getNumCurrentlySubmittedTasks()).isEqualTo(0L);
						BDDAssertions.assertThat(metrics.getTotalNumSubmittedTasks())
								.isEqualTo(metricsSummaryBeforeTest.getTotalNumSubmittedTasks() + 1L);
						BDDAssertions.assertThat(metrics.getTotalNumExecutedTasks())
								.isEqualTo(metricsSummaryBeforeTest.getTotalNumExecutedTasks() + 1L);
					});
				});
	}

}
