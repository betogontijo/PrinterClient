package br.pucminas.printerclient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Performs broadcast and multicast peer detection. How well this works depends
 * on your network configuration
 * 
 * @author ryanm
 */
public class PeerDiscovery {
	/**
	 * The group identifier. Determines the set of peers that are able to discover
	 * each other
	 */
	public final int group;

	/**
	 * The port number that we operate on
	 */
	public final int port;

	/**
	 * Data returned with discovery
	 */
	public int peerData;

	private final DatagramSocket bcastSocket;

	private final InetSocketAddress broadcastAddress;

	private boolean shouldStop = false;

	private Set<Peer> responseList = new HashSet<Peer>();

	/**
	 * Used to detect and ignore this peers response to it's own query. When we send
	 * a response packet, we set this to the destination. When we receive a
	 * response, if this matches the source, we know that we're talking to ourselves
	 * and we can ignore the response.
	 */
	private Set<InetAddress> localAddresses = new HashSet<InetAddress>();

	/**
	 * Redefine this to be notified of exceptions on the listen thread. Default
	 * behaviour is to print to stdout. Can be left as null for no-op
	 */
	public ExceptionHandler rxExceptionHandler = new ExceptionHandler();

	{
		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
		while (networkInterfaces.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			Enumeration<InetAddress> enumInetAddresses = networkInterface.getInetAddresses();
			while (enumInetAddresses.hasMoreElements()) {
				InetAddress address = enumInetAddresses.nextElement();
				if (address instanceof Inet4Address && !address.getHostAddress().equals("127.0.0.1")) {
					localAddresses.add(address);
				}
			}
		}
	}

	private Thread bcastListen = new Thread(PeerDiscovery.class.getSimpleName() + " broadcast listen thread") {
		@Override
		public void run() {
			try {
				byte[] buffy = new byte[5];
				DatagramPacket rx = new DatagramPacket(buffy, buffy.length);

				while (!shouldStop) {
					try {
						buffy[0] = (byte) port;

						bcastSocket.receive(rx);
						DatagramPacket tx = new DatagramPacket(buffy, buffy.length, rx.getAddress(), port);

						bcastSocket.send(tx);
						if (!localAddresses.contains(rx.getAddress())) {
							synchronized (responseList) {
								responseList.add(new Peer(rx.getAddress(), buffy[0]));
							}
						}
					} catch (SocketException se) {
						// someone may have called disconnect()
					}
				}

				bcastSocket.disconnect();
				bcastSocket.close();
			} catch (Exception e) {
				if (rxExceptionHandler != null) {
					rxExceptionHandler.handle(e);
				}
			}
		};
	};

	/**
	 * Constructs a UDP broadcast-based peer
	 * 
	 * @param group
	 *            The identifier shared by the peers that will be discovered.
	 * @param port
	 *            a valid port, i.e.: in the range 1025 to 65535 inclusive
	 * @throws IOException
	 */
	public PeerDiscovery(int group, int port) throws IOException {
		this.group = group;
		this.port = port;

		bcastSocket = new DatagramSocket(port);
		bcastSocket.setBroadcast(true);
		
		broadcastAddress = new InetSocketAddress("255.255.255.255", port);

		bcastListen.setDaemon(true);
		bcastListen.start();
	}

	/**
	 * Signals this {@link PeerDiscovery} to shut down. This call will block until
	 * everything's timed out and closed etc.
	 */
	public void disconnect() {
		shouldStop = true;

		bcastSocket.close();
		bcastSocket.disconnect();

		try {
			bcastListen.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Queries the network and finds the addresses of other peers in the same group
	 * 
	 * @param timeout
	 *            How long to wait for responses, in milliseconds. Call will block
	 *            for this long, although you can {@link Thread#interrupt()} to cut
	 *            the wait short
	 * @param peerType
	 *            The type flag of the peers to look for
	 * @return The addresses of other peers in the group
	 * @throws IOException
	 *             If something goes wrong when sending the query packet
	 */
	public Set<Peer> getPeers(int timeout) throws IOException {

		// send query byte, appended with the group id
		byte[] data = new byte[5];
		data[0] = (byte) port;
		// encode(group, data, 1);

		responseList = new HashSet<Peer>();

		DatagramPacket tx = new DatagramPacket(data, data.length, broadcastAddress);

		bcastSocket.send(tx);

		// wait for the listen thread to do its thing
		try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
		}

		return responseList;
	}

	public Set<InetAddress> getLocalAddresses() {
		return localAddresses;
	}

	/**
	 * Record of a peer
	 * 
	 * @author ryanm
	 */
	public class Peer {
		/**
		 * The ip of the peer
		 */
		private final InetAddress ip;

		/**
		 * The data of the peer
		 */
		private final int port;

		private Peer(InetAddress ip, int port) {
			this.ip = ip;
			this.port = port;
		}

		@Override
		public String toString() {
			return getIp().getHostAddress() + " " + getPort();
		}

		@Override
		public boolean equals(Object obj) {
			Peer other = (Peer) obj;
			if (this.getIp().equals(other.getIp())) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return getIp().hashCode();
		}

		public InetAddress getIp() {
			return ip;
		}

		public int getPort() {
			return port;
		}
	}

	/**
	 * Handles an exception.
	 * 
	 * @author ryanm
	 */
	public class ExceptionHandler {
		/**
		 * Called whenever an exception is thrown from the listen thread. The listen
		 * thread should now be dead
		 * 
		 * @param e
		 */
		public void handle(Exception e) {
			e.printStackTrace();
		}
	}
}