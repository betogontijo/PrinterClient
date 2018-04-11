package br.pucminas.printerclient;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import br.pucminas.printerclient.PeerDiscovery.Peer;

public class Radar extends TimerTask {

	private List<Peer> peers;
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
			setPeers(mp.getPeers(100, (byte) 0));
			System.out.println(getPeers());
			new Timer().schedule(new Radar(group, port, mp), 1000);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public List<Peer> getPeers() {
		return peers;
	}

	public void setPeers(List<Peer> peers) {
		this.peers = peers;
	}

	public static void main(String[] args) throws IOException {
		Radar radar = new Radar(6969, 6969);
		radar.run();
	}

}
