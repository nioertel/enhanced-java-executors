package io.github.nioertel.async.task.registry.state;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * State accessor.
 *
 * @param <R>
 *            The type that provides the external view on the state object (e.g. an interface that is implemented by the
 *            state).
 * @param <T>
 *            The state type.
 */
public final class StateAccessor<T extends Versioned, R> {

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

	private final WriteLock writeLock = lock.writeLock();

	private final ReadLock readLock = lock.readLock();

	private final T state;

	private final Function<T, R> stateCloner;

	public StateAccessor(Supplier<T> newStateCreator, Function<T, R> stateCloner) {
		this.state = newStateCreator.get();
		this.stateCloner = stateCloner;
	}

	/**
	 * Perform a write operation on the state and return the result.
	 * NOTE: This operation will create a write lock on the state (i.e. while the operation is running, the state is blocked
	 * from other access).
	 *
	 * @param <S>
	 *            The result type.
	 * @param triggeringOperation
	 *            The operation that triggered the update.
	 * @param command
	 *            The command to be run on the state object.
	 * @param changeListener
	 *            The change listener to be notified.
	 *
	 * @return The result of the provided command.
	 */
	public R update(String triggeringOperation, Consumer<T> command, StateChangeListener<R> changeListener) {
		R currentStateClone;
		writeLock.lock();
		try {
			command.accept(state);
			state.setLastOperation(triggeringOperation);

			// NOTE: we could convert the write lock into a read lock here
			currentStateClone = stateCloner.apply(state);
			state.incrementVersion();
		} finally {
			writeLock.unlock();
		}
		changeListener.stateChanged(triggeringOperation, currentStateClone);
		return currentStateClone;
	}

	/**
	 * Perform a write operation on the state and return its result.
	 * NOTE: This operation will create a write lock on the state (i.e. while the operation is running, the state is blocked
	 * from other access).
	 *
	 * @param triggeringOperation
	 *            The operation that triggered the update.
	 * @param command
	 *            The command to be run on the state object.
	 * @param changeListener
	 *            The change listener to be notified.
	 */
	public <S> S update(String triggeringOperation, Function<T, S> command, StateChangeListener<R> changeListener) {
		return updateInternal(//
				triggeringOperation, //
				command, //
				changeListener//
		);
	}

	/**
	 * Perform a write operation on the state.
	 * NOTE: This operation will create a write lock on the state (i.e. while the operation is running, the state is blocked
	 * from other access).
	 *
	 * @param triggeringOperation
	 *            The operation that triggered the update.
	 * @param command
	 *            The command to be run on the state object.
	 * @param changeListener
	 *            The change listener to be notified.
	 */
	public void updateWithoutResult(String triggeringOperation, Consumer<T> command, StateChangeListener<R> changeListener) {
		updateInternal(//
				triggeringOperation, //
				state -> {
					command.accept(state);
					return null;
				}, //
				changeListener//
		);
	}

	/**
	 * Perform a write operation on the state.
	 * NOTE: This operation will create a write lock on the state (i.e. while the operation is running, the state is blocked
	 * from other access).
	 *
	 * @param <S>
	 *            The result type.
	 * @param triggeringOperation
	 *            The operation that triggered the update.
	 * @param command
	 *            The command to be run on the state object.
	 * @param operationResultConsumer
	 *            An optional consumer that receives the result of the operation.
	 * @param changeListener
	 *            The change listener to be notified.
	 *
	 * @return The result of the provided command.
	 */
	private <S> S updateInternal(String triggeringOperation, Function<T, S> command, StateChangeListener<R> changeListener) {
		S result;
		R currentStateClone;

		writeLock.lock();
		try {
			result = command.apply(state);
			state.setLastOperation(triggeringOperation);
			// make it a little harder to leak the state to the outside!
			if (result == state) {
				throw new UnsupportedOperationException("Leaking the state out of the change operation is forbidden!");
			}
			state.incrementVersion();
			// if we need to inform a change listener, create a clone of the state here so we can release the lock
			// NOTE: we could convert the write lock into a read lock here
			if (null != changeListener) {
				currentStateClone = stateCloner.apply(state);
			} else {
				// This code is only required because Java compiler can't know that we won't access it later
				currentStateClone = null;
			}
		} finally {
			writeLock.unlock();
		}
		if (null != changeListener) {
			changeListener.stateChanged(triggeringOperation, currentStateClone);
		}
		return result;
	}

	/**
	 * Perform a read operation on the state and return the result.
	 * NOTE: This operation will create a read lock on the state (i.e. while the operation is running, the state is blocked
	 * from write access).
	 *
	 * @param <S>
	 *            The result type.
	 * @param extractor
	 *            The extractor to apply on the state.
	 *
	 * @return The result of the provided extractor.
	 */
	public <S> S extract(Function<T, S> extractor) {
		readLock.lock();
		try {
			return extractor.apply(state);
		} finally {
			readLock.unlock();
		}
	}

	/**
	 * Create a consistent snapshot of the current state.
	 * NOTE: This operation will create a read lock on the state (i.e. while the operation is running, the state is blocked
	 * from write access).
	 *
	 * @return The state snapshot.
	 */
	public R snapshot() {
		return extract(stateCloner);
	}
}
