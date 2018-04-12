package br.pucminas.printerclient;

import java.io.IOException;

public class PrinterClientMain {

	public static void main(String[] args) throws IOException {
		new Driver(Integer.parseInt(args[0]));
	}

}
