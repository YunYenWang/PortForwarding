package org.idea.net;

public class PortForwardingTest {

	public static void main(String[] args) throws Exception {
		String[] p = new String[] {
				"-b", "0.0.0.0:22",
				"-f", "10.144.77.90:22",
				"-t", "5000"
		};
		
		PortForwarding.main(p);
	}
}
