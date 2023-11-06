package io.github.nioertel.async.task.registry.state;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class StateChangeLstenerNotifier<T> {

	private Executor listenerExecutor = Runnable::run;

	private final List<StateChangeListener<T>> changeListeners = new CopyOnWriteArrayList<>();

	private StateChangeListener<T> listenerWrapper;

	public StateChangeListener<T> getListenerWrapper() {
		return listenerWrapper;
	}

	/**
	 * Override the default executor for state change listeners which runs them synchronously as part of the main
	 * operations. This may be used to move to an asynchronous processing of change listeners.
	 *
	 * @param executor
	 *            The executor.
	 */
	public void setStateChangeListenerExecutor(Executor executor) {
		this.listenerExecutor = executor;
	}

	/**
	 * Register a state change listener. Note that state change listeners are by default executed synchronously during
	 * operations and should therefore finish very fast.
	 * To override this default behaviour see {@link #setStateChangeListenerExecutor(Executor)}.
	 *
	 * @param stateChangeListener
	 *            The state change listener.
	 */
	public void registerStateChangeListener(StateChangeListener<T> stateChangeListener) {
		changeListeners.add(stateChangeListener);
		if (!changeListeners.isEmpty()) {
			listenerWrapper = (lastCommand, state) -> {
				changeListeners.forEach(listener -> listenerExecutor.execute(() -> listener.stateChanged(lastCommand, state)));
			};
		}
	}

}