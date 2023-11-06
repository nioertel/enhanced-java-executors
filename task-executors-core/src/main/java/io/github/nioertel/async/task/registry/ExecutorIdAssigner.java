package io.github.nioertel.async.task.registry;

public interface ExecutorIdAssigner {

	public ExecutorIdAssignment assignExecutorId(TaskRegistryInfoAccessor registryInfoAccessor, TaskState taskDetails);
}
