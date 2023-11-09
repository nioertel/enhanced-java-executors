# enhanced-task-executors
Specialized implementations of Java's `ExecutorService`.

----------------------------------------------------
## Contents
* [Module: task-executors-core](#task-executors-core)
* [Module: task-executors-spring-boot-27](#task-executors-spring-boot-27)
* [Module: task-executors-test-helper](#task-executors-test-helper)

----------------------------------------------------
## task-executors-core
### Problem statement
- There are tasks which need to be executed in different executors
- The different executors need different configurations
- The executor assignment should be done transparently in the background requires complex logic based on the currently executing tasks

### Goals of this plugin
- Provide implementations of `java.util.concurrent.ExecutorService` for solving more complex scheduling requirements
- Add minimal performance overhead
- Provide extensive monitoring capabilities / insights via metrics

### Components
This module brings different implementations of Java's `java.util.concurrent.ExecutorService` (see below), allowing configuration of multiple thread pools and transparent automatic assignment of submitted tasks. 
In its core the solution consists of the following components:
- `TaskRegistry`:
  - A registry that keeps track of the tasks, their lifecycle and their relationships
  - The registry is passive and receives updates via standardized a interface: `taskSubmitted(...)`, `taskExecutionStarted(...)`, `taskExecutionFinished(...)`, `taskDiscarded(...)`
  - When submitting a task, the registry takes assigns an executor based on the registered `ExecutorIdAssigner` (see below)
  - When submitting a task it gets a task family assigned:
    - If the task's parent (based on submitting thread id) is also registered in the registry, the new task is added to the same family
    - Otherwise a new task family is created
  - Tasks can be parked during executor assignment and resbumitted later (`resubmitParkedTasks()`)
  - Metrics can be retrieved via pull (`getMetricsSnapshot()`) or push (`registerStateChangeListener(StateChangeListener<TaskRegistryState> stateChangeListener)`)
  - Global task state can be retrieved via pull (`getStateSnapshot()`) or push (`registerMetricsChangeListener(StateChangeListener<TaskRegistryMetrics> stateChangeListener)`)
  - The registry can also be used independently of the executors
- `ExecutorIdAssigner`:
  - Provides the interface to control executor assignment
  - Tasks can be assigned directly (`ExecutorIdAssignmentCommand.ASSIGN`) or parked for later assignment (`ExecutorIdAssignmentCommand.PARK`)
- Executors:
  - [MultiThreadPoolExecutor](#MultiThreadPoolExecutor):
    - This is the most generic solution
    - Configure the parameters of your backing thread pools
    - Implement your own `ExecutorIdAssigner`
    - Done :)
  - [BurstingThreadPoolExecutor](#BurstingThreadPoolExecutor):
    - This one is the gem of the bundle :)
    - It is a specialized version of the `MultiThreadPoolExecutor` which by default executes tasks on the main pool
    - If the main pool is full and a new task is submitted, burst mode is entered:
      - In case there are tasks from less than `burstMDOP` task families currently active in the secondary executor, the task is submitted there
      - Otherwise the task is parked for later (automatic) resubmission

### Usage
#### Maven
```xml
<dependency>
    <groupId>io.github.nioertel.async</groupId>
    <artifactId>task-executors-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### MultiThreadPoolExecutor
TODO: Add documentation

#### BurstingThreadPoolExecutor
Java Code Snippet (usage is exactly the same as for normal `ThreadPoolTaskExecutor`):
```java
// ...
import io.github.nioertel.async.task.executor.BurstingThreadPoolExecutor;
import io.github.nioertel.async.task.registry.listeners.LoggingTaskRegistryStateChangeListener;
import io.github.nioertel.async.task.registry.listeners.LoggingMetricsStateChangeListener;
// ...

// ... boiler plate code

BurstingThreadPoolExecutor executor = BurstingThreadPoolExecutor.newBurstingThreadPoolExecutor(//
        1 // corePoolSize
        , 1 // maximumPoolSize
        , 1 // burstCorePoolSize
        , 1 // burstMaximumPoolSize
        , 60 // keepAliveTime
        , TimeUnit.SECONDS // unit
        , new LinkedBlockingQueue<>(10) // workQueue
        , new LinkedBlockingQueue<>(10) // burstWorkQueue
        , Executors.defaultThreadFactory() // threadFactory
        , new ThreadPoolExecutor.AbortPolicy() // rejectedExecutionHandler
);
executor.setBurstExecutionMDOP(2);
// NOT for production use(!!!)
executor.registerStateChangeListener(new LoggingTaskRegistryStateChangeListener());
executor.registerMetricsChangeListener(new LoggingMetricsStateChangeListener());

try {
    Runnable task1 = () -> System.out.println("Hello world");

    // nothing should be running yet
    System.out.println(executor.getStateSnapshot().getCurrentlyExecutingTasks());

    // submit first task and wait until task has finished
    Future<?> result1 = executor.submit(task1);
    result1.get();

    // nothing should be running anymore
    System.out.println(executor.getStateSnapshot().getCurrentlyExecutingTasks());
} finally {
    executor.shutdown();
}
```

----------------------------------------------------
## task-executors-spring-boot-27
When using Spring Boot, there is an auto configuration in place (`TaskRegistryInsightsEndpointsAutoConfiguration`).
The auto configuration creates acuator and metrics integrations if it finds at least on `TaskRegistryInsightsRetriever` bean in the application context, which can be created using the following code snipped:
```java
  @Bean
  TaskRegistryInsightsRetriever taskRegistryInsightsRetriever(RegistryBackedExecutorService executor) {
    return new TaskRegistryInsightsRetriever(//
      "my-task-registry", // registryName
      executor::getStateSnapshot, // stateSupplier
      executor::getMetricsSnapshot, // metricsSupplier
      10_000 // micrometerMetricsChangePublishingIntervalMillis
    );
  }
```

The following integrations will be creatd:
- Actuator endpoint `taskRegistryMetrics`: Provides a snapshot of the metrics per task registry (i.e. per registered `TaskRegistryInsightsRetriever`)
- Actuator endpoint `taskRegistryState`: Provides a snapshot of the state per task registry (i.e. per registered `TaskRegistryInsightsRetriever`)
- Micrometer metrics for each task registry (i.e. per registered `TaskRegistryInsightsRetriever`; only if a `io.micrometer.core.instrument.MeterRegistry` is present in the appliction context):
  - Gauge `task_registry_state_version`
  - Gauge `task_registry_num_currently_submitted_tasks`
  - Gauge `task_registry_num_currently_executing_tasks`
  - Gauge `task_registry_num_currently_parked_tasks`
  - Gauge `task_registry_total_num_submitted_tasks`
  - Gauge `task_registry_total_num_executed_tasks`
  - Gauge `task_registry_total_num_discarded_tasks`
  - Timer `task_registry_executor_assignment_wait_time`
  - Timer `task_registry_execution_start_wait_time`
  - Timer `task_registry_execution_time`
Note: The metrics will be updated only in the configured interval.

----------------------------------------------------
## task-executors-test-helper
Tools for testing of the task-executors. Only for internal use currently.
