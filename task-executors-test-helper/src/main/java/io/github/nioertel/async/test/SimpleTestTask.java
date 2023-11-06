package io.github.nioertel.async.test;

public final class SimpleTestTask implements Runnable {

	private final String name;

	public SimpleTestTask(String name) {
		this.name = name;
	}

	@Override
	public void run() {
		System.out.println("Task [" + name + "] waiting for execution to start.");
		System.out.println("Task [" + name + "] finished.");
	}
}