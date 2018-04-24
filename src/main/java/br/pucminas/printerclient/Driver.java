package br.pucminas.printerclient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.pucminas.printerclient.PeerDiscovery.Peer;

public class Driver {
	// For convenience in accessing channels; will contain our writers above
	ArrayList<PrintWriter> outputStreams;

	// Readers that will be passed to a separate thread of execution each
	List<BufferedReader> inputStreams;

	int nodeNum;

	// Our mutual exclusion algorithm object for this node
	RicartAgrawala me;

	// int numberOfWrites;
	// int writeLimit = 100; // number of times to try CS
	int csDelay = 200; // wait delay between CS tries in ms

	private ExecutorService exec;

	/**
	 * Start the driver, with a number of channels specified.
	 * 
	 * @throws IOException
	 **/
	static BufferedWriter criticalSection = null;

	@SuppressWarnings("resource")
	public Driver() throws IOException {
		int port = 7000;
		Radar radar = new Radar(port, port);
		radar.start();
		String localAddress = radar.getLocalAddress();
		new Printer(9000).start();
		List<Peer> ips = new ArrayList<Peer>(radar.getPeers());

		while (true) {
			try {
				if (!ips.equals(new ArrayList<Peer>(radar.getPeers()))) {
					ips = new ArrayList<Peer>(radar.getPeers());
					if (!ips.isEmpty()) {
						ArrayList<String> cluster = new ArrayList<String>();
						cluster.add(localAddress);
						for (Peer peer : ips) {
							cluster.add(peer.getIp().getHostAddress());
						}
						cluster.sort(new Comparator<String>() {
							@Override
							public int compare(String o1, String o2) {
								return o1.compareTo(o2);
							}
						});
						criticalSection = new BufferedWriter(
								new OutputStreamWriter(new Socket(cluster.get(0), 9000).getOutputStream()));
						this.nodeNum = cluster.indexOf(localAddress) + 1;
						initDriver(port + 1, ips);
					}
				}
				if (!ips.isEmpty()) {
					System.out.println("Requesting critical section...");
					requestCS();
					// numberOfWrites++;
					Random num = new Random();
					Thread.sleep(num.nextInt(500));
					// Thread.sleep(csDelay);
				} else {
					System.out.print("Waiting for connection...\r");
				}
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void initDriver(int initialPort, List<Peer> ips) {
		// Set up our sockets with our peer nodes
		try {
			List<ServerSocket> ss = new ArrayList<ServerSocket>();
			List<Socket> s = new ArrayList<Socket>();
			outputStreams = new ArrayList<PrintWriter>();
			inputStreams = new ArrayList<BufferedReader>();
			// Clear the file
			BufferedWriter clearWrite = new BufferedWriter(new FileWriter("CriticalSectionOutput.txt"));
			clearWrite.write("\n");
			clearWrite.close();

			System.out.println("Node " + nodeNum + " here");
			for (int i = 0; i < nodeNum - 1; i++) {
				int j = initialPort++;
				Socket socket = new Socket(ips.get(i).getIp(), j);
				while (!socket.isConnected()) {
					socket.connect(socket.getRemoteSocketAddress());
				}
				s.add(socket);
			}
			for (int i = nodeNum - 1; i < ips.size(); i++) {
				ss.add(new ServerSocket(initialPort++));
			}
			for (ServerSocket serverSocket : ss) {
				s.add(serverSocket.accept());
			}
			System.out.println("Created all sockets");

			// With the sockets done, create our readers and writers
			for (Socket socket : s) {
				outputStreams.add(new PrintWriter(socket.getOutputStream(), true));
				inputStreams.add(new BufferedReader(new InputStreamReader(socket.getInputStream())));
			}

			// Create the ME object with priority of 'nodeNum' and initial sequence number 0
			me = new RicartAgrawala(nodeNum, 0, ips.size(), this);
			me.w = outputStreams;

			if (exec != null) {
				exec.shutdownNow();
			}

			exec = Executors.newFixedThreadPool(s.size());
			for (int j = 0; j < s.size(); j++) {
				Socket socket3 = s.get(j);
				exec.execute(new ChannelHandler(socket3));
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Invocation of Critical Section */
	public static boolean criticalSection(int nodeNum) {
		try {

			criticalSection.write(nodeNum + " holds critical section access");
			criticalSection.flush(); // flush stream
			Thread.sleep(1000);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	/**
	 * Interface method between Driver and RicartAgrawala
	 */
	public void requestCS() {

		me.invocation();

		// After invocation returns, we can safely call CS
		criticalSection(nodeNum);

		// Once we are done with CS, release CS
		me.releaseCS();
	}

	/**
	 * Broadcasts a message to all writers in the outputStreams arraylist. Note this
	 * should probably never be used as RicartAgrawala is unicast
	 */
	public void broadcast(String message) {
		for (int i = 0; i < outputStreams.size(); i++) {
			try {
				PrintWriter writer = outputStreams.get(i);
				writer.println(message);
				writer.flush();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Given a socket, it continuously reads from the socket and passes key
	 * information to the ME object.
	 */
	class ChannelHandler implements Runnable {
		BufferedReader reader;
		PrintWriter writer;
		Socket sock;

		public ChannelHandler(Socket s) {
			try {
				sock = s;
				InputStreamReader iReader = new InputStreamReader(sock.getInputStream());
				reader = new BufferedReader(iReader);

			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		/** Continuously runs and reads all incoming messages, passing messages to ME */

		public void run() {
			String message;

			try {
				// As long as this reader is open, will take action the moment a message
				// arrives.
				while ((message = reader.readLine()) != null) {
					System.out.println(message);

					// Tokenize our message to determine RicartAgrawala step

					String tokens[] = message.split(",");
					String messageType = tokens[0];

					if (messageType.equals("REQUEST")) {
						/*
						 * We are receiving request(j,k) where j is a seq# and k a node#. This call will
						 * decide to defer or ack with a reply.
						 */
						me.receiveRequest(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
					} else if (messageType.equals("REPLY")) {
						/* Received a reply. We'll decrement our outstanding replies */
						me.receiveReply();
					}
				}

			} catch (Exception ex) {
				try {
					sock.close();
				} catch (IOException e) {
				}
				return;
			}
		}
	}

}