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
	
	private Worker worker;

	public Network() {
		worker = new Worker();

		worker.setAcceptable(key -> {
			// this means that a new client has hit the port our main
            // socket is listening on, so we need to accept the  connection
            // and add the new client socket to our select pool for reading
            // a command later
            System.out.println("Accepting connection!");
            // this will be the ServerSocketChannel we initially registered
            // with the selector in main()
            ServerSocketChannel sch = (ServerSocketChannel)key.channel();
			try {
				SocketChannel ch = sch.accept();
				ch.configureBlocking(false);
			} catch(IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            // ch.register(this.sel, SelectionKey.OP_READ);
		});
	}
	
	public void startServer() {
		startServer(new InetSocketAddress(25565));
	}
	
	public void startServer(InetSocketAddress address) {
		
		try {
			ServerSocketChannel sch = ServerSocketChannel.open();
			sch.configureBlocking(false);
			sch.bind(address);
			
			worker.addMaintenance(sel -> {
				try {
					sch.register(sel, SelectionKey.OP_ACCEPT);
				} catch(ClosedChannelException e) {
					e.printStackTrace();
				}
			});
			
			new Thread(worker).start();
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
