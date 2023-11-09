package io.github.nioertel.async.task.executor;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestRunnable implements Runnable {

	private AtomicBoolean coreLogicFinished = new AtomicBoolean();

	private Semaphore canFinishExecution = new Semaphore(0);

	private int result;

	private void doSomethingExpensive() {
		int hash = 0;
		for (int i = 0; i < 10_000; ++i) {
			hash += ("run-" + i).hashCode();
		}
		this.result = hash;
		try {
			TimeUnit.MILLISECONDS.sleep(10L);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public synchronized void run() {
		doSomethingExpensive();
		coreLogicFinished.set(true);

		try {
			canFinishExecution.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			coreLogicFinished.set(false);
		}
	}

	public int getResult() {
		return result;
	}

	public void continueRunning() {
		canFinishExecution.release();
	}

	public void reset() {
		continueRunning();
		blockUntilExecutionFinished(10L, TimeUnit.SECONDS);
	}

	public boolean blockUntilCoreLogicFinished(long timeout, TimeUnit unit) {
		long latestEndOfBlocking = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout, unit);
		while (latestEndOfBlocking > System.currentTimeMillis()) {
			if (coreLogicFinished.get()) {
				return true;
			} else {
				try {
					TimeUnit.MILLISECONDS.sleep(Math.min(latestEndOfBlocking - System.currentTimeMillis(), 10L));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return coreLogicFinished.get();
				}
			}
		}
		return false;
	}

	public boolean blockUntilExecutionFinished(long timeout, TimeUnit unit) {
		long latestEndOfBlocking = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(timeout, unit);
		while (latestEndOfBlocking > System.currentTimeMillis()) {
			if (!coreLogicFinished.get()) {
				return true;
			} else {
				try {
					TimeUnit.MILLISECONDS.sleep(Math.min(latestEndOfBlocking - System.currentTimeMillis(), 10L));
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return !coreLogicFinished.get();
				}
			}
		}
		return false;
	}
}