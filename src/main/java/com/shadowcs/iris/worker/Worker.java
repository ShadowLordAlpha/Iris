package com.shadowcs.iris.worker;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class Worker implements Runnable, AutoCloseable {

	private static final Consumer<SelectionKey> ERROR_DEFAULT = key -> System.err.println(String.format("Unaccepted Key: %s, ops: %d", key, key.readyOps()));

	private Selector selector;

	private Queue<Consumer<Selector>> maintenanceQueue;

	// The nice thing about this is that I can have several workers splitting the work or one doing everything
	private Consumer<SelectionKey> acceptable;
	private Consumer<SelectionKey> connectable;
	private Consumer<SelectionKey> readable;
	private Consumer<SelectionKey> writable;
	private Consumer<SelectionKey> error = ERROR_DEFAULT;

	public Worker() {

		maintenanceQueue = new ConcurrentLinkedQueue<Consumer<Selector>>();

		try {
			selector = Selector.open();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	// These are threaded and so we have no idea when something will happen and as such they are not allowed to be
	// removed.
	// The can be canceled internally though but that functionality would need to be done though the consumer and is not
	// provided
	// this will also call wakeup on the workers selector
	public Worker addMaintenance(Consumer<Selector> maint) {

		maintenanceQueue.add(maint);
		selector.wakeup();

		return this;
	}

	@Override
	public void run() {

		while(!Thread.currentThread().isInterrupted()) {

			try {
				int numKeys = selector.select();

				// selector maintenance, does stuff that should take place on
				// the selectors thread because things like to break if they are not done there
				maintenanceQueue.forEach(con -> con.accept(selector));

				// Nothing to do other than selector maintenance
				if(numKeys == 0) {
					continue;
				}

				// There are two methods I could use here. one is throwing the keys as events and letting event
				// listeners
				// handle them the other is to use passed in consumers and let those throw the events. For now we will
				// use
				// the second method
				Set<SelectionKey> keySet = selector.selectedKeys();
				keySet.forEach(this::KeySetLoop);

			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void setAcceptable(Consumer<SelectionKey> acceptable) {
		this.acceptable = acceptable;
	}

	public void setConnectable(Consumer<SelectionKey> connectable) {
		this.connectable = connectable;
	}

	public void setReadable(Consumer<SelectionKey> readable) {
		this.readable = readable;
	}

	public void setWritable(Consumer<SelectionKey> writable) {
		this.writable = writable;
	}

	public void setError(Consumer<SelectionKey> error) {
		this.error = error;
	}

	private void KeySetLoop(SelectionKey key) {

		// This should be valid but only if the set returned is the same until select() is returned again
		key.selector().selectedKeys().remove(key);

		// The key is not valid so we can simply return
		if(!key.isValid()) { return; }

		if((acceptable != null) && key.isAcceptable()) {
			acceptable.accept(key);
		} else if((connectable != null) && key.isConnectable()) {
			connectable.accept(key);
		} else if((readable != null) && key.isReadable()) {
			readable.accept(key);
		} else if((writable != null) && key.isWritable()) {
			writable.accept(key);
		} else {
			// TODO: this is an error state
			// We might be able to skip this state if we swap the if statements but needs testing
			error.accept(key);
		}
	}

	@Override
	public void close() {

		// This should properly stop the running thread though it will run though the rest of the loop before it is
		// done.
		// we directly add it to the queue because we don't actually want wakeup to be run on the selector as we want to
		// use close to do the wakeup and calling wakeup after would throw an exception
		maintenanceQueue.add(sel -> Thread.currentThread().interrupt());
		try {
			selector.close();
		} catch(IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
