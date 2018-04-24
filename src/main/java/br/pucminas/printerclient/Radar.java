package br.pucminas.printerclient;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import br.pucminas.printerclient.PeerDiscovery.Peer;

public class Radar extends Thread {

	private Set<Peer> peers = new HashSet<Peer>();
	PeerDiscovery mp;
	int group;
	int port;

	public Radar(int group, int port) throws IOException {
		this.group = group;
		this.port = port;
		mp = new PeerDiscovery(group, port);
	}

	public Radar(int group, int port, PeerDiscovery mp) throws IOException {
		this.group = group;
		this.port = port;
		this.mp = mp;
	}

	@Override
	public void run() {
		try {
			while (true) {
				setPeers(mp.getPeers(1000));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Set<Peer> getPeers() {
		return peers;
	}

	public void setPeers(Set<Peer> peers) {
		this.peers = peers;
	}

	public String getLocalAddress() {
		Iterator<InetAddress> iterator = this.mp.getLocalAddresses().iterator();
		while (iterator.hasNext()) {
			InetAddress next = iterator.next();
			if (!next.getHostAddress().startsWith("169") && !next.getHostAddress().startsWith("192")) {
				return next.getHostAddress();
			}
		}
		return null;
	}
}
