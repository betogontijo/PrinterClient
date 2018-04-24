package br.pucminas.printerclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Printer extends Thread {

	int port;

	public Printer(int port) {
		this.port = port;
	}

	@SuppressWarnings("resource")
	public void run() {
		try {
			ServerSocket ss = new ServerSocket(port);
			while (true) {
				final Socket sock = ss.accept();
				new Thread() {
					@Override
					public void run() {
						try {
							InputStreamReader iReader = new InputStreamReader(sock.getInputStream());
							BufferedReader reader = new BufferedReader(iReader);
							String message;
							// As long as this reader is open, will take action the moment a message
							// arrives.
							while ((message = reader.readLine()) != null) {
								System.out.println(message);
							}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}.start();
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

}
