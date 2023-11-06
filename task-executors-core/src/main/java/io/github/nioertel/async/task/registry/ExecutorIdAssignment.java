package io.github.nioertel.async.task.registry;

public class ExecutorIdAssignment {

	public enum ExecutorIdAssignmentCommand {
		ASSIGN,
		PARK
	}

	private final ExecutorIdAssignment.ExecutorIdAssignmentCommand command;

	private final long assignedExecutorId;

	public ExecutorIdAssignment(ExecutorIdAssignment.ExecutorIdAssignmentCommand command, long assignedExecutorId) {
		this.command = command;
		this.assignedExecutorId = assignedExecutorId;
	}

	public ExecutorIdAssignment.ExecutorIdAssignmentCommand getCommand() {
		return command;
	}

	public long getAssignedExecutorId() {
		return assignedExecutorId;
	}

}