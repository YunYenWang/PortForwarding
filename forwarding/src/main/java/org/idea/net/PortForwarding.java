package org.idea.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortForwarding {
	static final Logger LOG = LoggerFactory.getLogger(PortForwarding.class);
	
	int timeout = 5000;
	
	public PortForwarding() {	
	}
	
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	public Binder fork(String binding, String forwarding) throws IOException {
		String[] fs;
		
		fs = binding.split(":");		
		InetSocketAddress bindingAddress = new InetSocketAddress(fs[0], Integer.parseInt(fs[1]));
		
		fs = forwarding.split(":");
		InetSocketAddress forwardingAddress = new InetSocketAddress(fs[0], Integer.parseInt(fs[1]));
		
		return new Binder(bindingAddress, forwardingAddress);		
	}
	
	public static String toString(InetSocketAddress address) {
		return String.format("%s:%d", address.getAddress().getHostAddress(), address.getPort());
	}
	
	public static String toString(Socket socket) {
		return String.format("%s:%d", socket.getInetAddress().getHostAddress(), socket.getPort());
	}
	
	class Binder {
		final ServerSocket server;
		
		Thread thread;
		
		public Binder(final InetSocketAddress bindingAddress, final InetSocketAddress forwardingAddress) throws IOException {
			server = new ServerSocket();
			server.bind(bindingAddress);
			
			LOG.info("Listen at {}", PortForwarding.toString(bindingAddress));
			
			thread = new Thread(new Runnable() {
				public void run() {					
					while (thread != null) {
						try {
							Socket sck = server.accept();
							
							LOG.info("Connected from {}", PortForwarding.toString(sck));
							
							new Forwarder(sck, forwardingAddress);							
							
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
					}
				}
			});
			thread.start();
		}
		
		public void destroy() throws IOException {
			thread = null;
			server.close();
		}
	}
	
	class Forwarder {
		Thread thread;
		
		public Forwarder(final Socket src, final InetSocketAddress forwardingAddress) {
			final String sname = PortForwarding.toString(src);
			final String dname = PortForwarding.toString(forwardingAddress);
			
			thread = new Thread(new Runnable() {
				public void run() {					
					try {
						LOG.info("To build a bridge connection from {} to {}", sname, dname);
						
						Socket dst = new Socket();
						dst.connect(forwardingAddress, timeout);
						
						new Fetcher(src, dst, src.getInputStream(), dst.getOutputStream());
						new Fetcher(dst, src, dst.getInputStream(), src.getOutputStream());
						
					} catch (Exception e) { // connection is timeout
						LOG.error("Failed to connect to {} - {}: {}", dname, e.getClass().getSimpleName(), e.getMessage());
						
						try {
							src.close();
							
						} catch (Exception ex) {
							LOG.error(ex.getMessage(), ex);
						}
					}
				}
			});
			thread.start();
		}
	}
	
	class Fetcher {
		static final int BUFFER_SIZE = 4096;
		Thread thread;
		
		public Fetcher(final Socket src, final Socket dst, final InputStream is, final OutputStream os) {
			final String sname = PortForwarding.toString(src);
			final String dname = PortForwarding.toString(dst);
			
			thread = new Thread(new Runnable() {
				public void run() {					
					try {
						byte[] bytes = new byte[BUFFER_SIZE];
						int s;
						while ((s = is.read(bytes)) > 0) {
							os.write(bytes, 0, s);
						}						
					} catch (Exception e) {
						LOG.error("Disconnected from {}", sname);
					}
					
					// HINT - another Fetcher will close the src socket.
					
					try {
						LOG.warn("To close {}", dname);						
						dst.close();						
						
					} catch (Exception e) {
						LOG.error(e.getMessage(), e);
					}
				}
			});
			thread.start();
		}
	}

	public static void main(String[] args) throws Exception {
		String binding = "0.0.0.0:2323";
		String forwarding = "bbs.ice.cycu.edu.tw:26";
		int timeout = 5000;
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("-b".equals(arg)) {
				binding = args[++i];
				
			} else if ("-f".equals(arg)) {
				forwarding = args[++i];
				
			} else if ("-t".equals(arg)) {
				timeout = Integer.parseInt(args[++i]);
				
			} else if ("-h".equals(arg)) {
				System.out.println("usage: java -jar PortForwarding.jar [-b address:port] [-f address:port] [-t timeout]");
				System.exit(0);
			}
		}		
		
		LOG.info("Prepare to forward TCP connection from {} to {}", binding, forwarding);
		
		PortForwarding pf = new PortForwarding();
		pf.setTimeout(timeout);
		pf.fork(binding, forwarding);
	}
}
