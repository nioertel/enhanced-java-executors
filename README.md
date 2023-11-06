# enhanced-task-executors
Specialized implementations of Java's `ExecutorService`.

----------------------------------------------------
## Contents
* [Module: task-executors-core](#task-executors-core)
* [Module: task-executors-spring5](#task-executors-spring5)
* [Module: task-executors-test-helper](#task-executors-test-helper)

----------------------------------------------------
## task-executors-core
### Goals of this plugin
- Provide implementations of `java.util.concurrent.ExecutorService` for solving more complex scheduling requirements
- Add minimal performance overhead
- Provide extensive monitoring capabilities / insights via metrics

### Usage
Maven:
```xml
<dependency>
    <groupId>io.github.nioertel.async</groupId>
    <artifactId>task-executors-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### BurstingThreadPoolExecutor
TODO: add problem description.

Java Code Snippet (usage is exactly the same as for normal `ThreadPoolTaskExecutor`):
```java
// ...
import io.github.nioertel.async.task.registry.listeners.LoggingTaskRegistryStateChangeListener;
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

try {
    Runnable task1 = () -> Syste.out.println("Hello world");

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
## task-executors-spring5

TODO: coming soon
----------------------------------------------------

## task-executors-test-helper
Tools for testing of the task-executors. Only for internal use currently.
