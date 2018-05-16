package br.pucminas.printerclient;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
								String[] ips1 = o1.split("\\.");
								String updatedIp1 = String.format("%3s.%3s.%3s.%3s", ips1[0], ips1[1], ips1[2],
										ips1[3]);
								String[] ips2 = o2.split("\\.");
								String updatedIp2 = String.format("%3s.%3s.%3s.%3s", ips2[0], ips2[1], ips2[2],
										ips2[3]);
								return updatedIp1.compareTo(updatedIp2);
							}
						});
						criticalSection = new BufferedWriter(
								new OutputStreamWriter(new Socket(cluster.get(0), 9000).getOutputStream()));
						this.nodeNum = cluster.indexOf(localAddress) + 1;
						initDriver(port + 1, cluster);
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

	Map<Integer, ServerSocket> mapServerSocket = new HashMap<Integer, ServerSocket>();
	// Map<String, Socket> mapSocket = new HashMap<String, Socket>();

	private void initDriver(int initialPort, List<String> ips) {
		// Set up our sockets with our peer nodes
		try {
			List<Socket> s = new ArrayList<Socket>();
			outputStreams = new ArrayList<PrintWriter>();
			inputStreams = new ArrayList<BufferedReader>();

			System.out.println("Node " + nodeNum + " here");
			for (int i = 0; i < ips.size(); i++) {
				int j = initialPort++;
				if (nodeNum - 1 != i) {
					String ip = ips.get(i > nodeNum - 1 ? i - 1 : i);
					// Socket socket2 = mapSocket.get(ip.getHostAddress() + ":" + j);
					boolean connected = false;
					while (!connected) {
						try {
							System.out.println("Connecting to server " + ip + ":" + j);
							Socket socket = new Socket(ip, j);
							connected = socket.isConnected();
							if (connected) {
								s.add(socket);
								// mapSocket.put(ip + ":" + j, socket);
							}
						} catch (Exception e) {
						}
					}
				} else {
					ServerSocket serverSocket2 = mapServerSocket.get(j);
					if (serverSocket2 == null) {
						serverSocket2 = new ServerSocket(j);
						// serverSocket.setReuseAddress(true);
						mapServerSocket.put(j, serverSocket2);
					}
					for (int k = 0; k < ips.size() - 1; k++) {
						System.out.println("Accepting at:" + j);
						s.add(serverSocket2.accept());
					}
				}
			}
			System.out.println("Created all sockets");

			// With the sockets done, create our readers and writers
			for (Socket socket : s) {
				outputStreams.add(new PrintWriter(socket.getOutputStream(), true));
				inputStreams.add(new BufferedReader(new InputStreamReader(socket.getInputStream())));
			}

			// Create the ME object with priority of 'nodeNum' and initial sequence number 0
			me = new RicartAgrawala(nodeNum, ips.size(), this);
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

			criticalSection.write(nodeNum + " holds critical section access\n");
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
						me.receiveRequest(Integer.parseInt(tokens[1]));
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