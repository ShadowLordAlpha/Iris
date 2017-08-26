package com.shadowcs.iris;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import com.shadowcs.iris.worker.Worker;

// Essentually just keeps all the workers nice and central so its easy to close them all when needed
// and contains some simple and reusable code to make setting up easier
public class Network implements AutoCloseable {

	public static final int SINGLE = 1;
	public static final int DUAL = 2;
	public static final int ALL = 3;

	// TODO: this needs to be reworked to support near unlimited workers as well as worker limits so that only x can connect to each worker
	// or something
	private Worker connectWorker;
	private Worker readWorker;
	private Worker writeWorker;

	public Network() {
		this(DUAL);
	}

	// TODO make mode an enum?
	public Network(int mode) {
		switch(mode) {
			case 1:
				connectWorker = new Worker();
				new Thread(connectWorker, "connect-worker").start();
				writeWorker = connectWorker;
				readWorker = connectWorker;
			case 2:
				readWorker = new Worker();
				new Thread(connectWorker, "read-worker").start();
				writeWorker = readWorker;
			case 3:
				writeWorker = new Worker();
				new Thread(connectWorker, "write-worker").start();
				break;
			default:
				// TODO error
				break;
		}
	}

	public Worker getConnectWorker() {
		return connectWorker;
	}

	public Worker getReadWorker() {
		return readWorker;
	}

	public Worker getWriteWorker() {
		return writeWorker;
	}

	// TODO: default worker operations
	
	public void startServer() {
		startServer(new InetSocketAddress(25565));
	}

	public void startServer(InetSocketAddress address) {

		try {
			ServerSocketChannel sch = ServerSocketChannel.open();
			sch.configureBlocking(false);
			sch.bind(address);

			getConnectWorker().addMaintenance(sel -> {
				try {
					sch.register(sel, SelectionKey.OP_ACCEPT);
				} catch(ClosedChannelException e) {
					e.printStackTrace();
				}
			});
		} catch(IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void startClient() {
		// TODO:
	}

	// we actually don't want to use the event manager to extensively
	// so as much as possible we don't internally as it could be considered
	// as waste of processing
	@Override
	public void close() throws Exception {

	}

	public static void main(String[] args) {
		new Network().startServer();

	}
}
