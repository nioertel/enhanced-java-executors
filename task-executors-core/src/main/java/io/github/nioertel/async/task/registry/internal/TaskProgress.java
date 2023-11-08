package io.github.nioertel.async.task.registry.internal;

public enum TaskProgress {

	// Task newly created
	CREATED,
	// Task waiting for executor assignment
	WAITING_FOR_EXECUTOR_ASSIGNMENT,
	// Executor has been assigned and task can be submitted for execution
	// EXECUTOR_ASSIGNED,
	// Task was parked during executor assignment
	PARKED,
	// Task has been parked for a considerable amount of time
	LONG_PARKED,
	// Executor has been assigned and task has been submitted for execution
	SUBMITTED,
	// Task is running
	RUNNING,
	// Task execution has finished
	FINISHED,
	// Task has been discarded. A reason could be that no executor assigned after waiting for a grace period.
	DISCARDED,
	// Task has been discarded while parked.
	DISCARDED_WHILE_PARKED,
	// Task has been discarded while already running.
	DISCARDED_WHILE_RUNNING
}
