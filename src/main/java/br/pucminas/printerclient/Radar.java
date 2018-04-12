package br.pucminas.printerclient;

import java.io.IOException;
import java.util.HashSet;
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
				setPeers(mp.getPeers(100));
				// System.out.println(getPeers());
				Thread.sleep(1000);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public Set<Peer> getPeers() {
		return peers;
	}

	public void setPeers(Set<Peer> peers) {
		this.peers = peers;
	}

	public static void main(String[] args) throws IOException {
		Radar radar = new Radar(6969, 6969);
		radar.run();
	}

}
