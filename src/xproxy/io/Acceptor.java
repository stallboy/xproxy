package xproxy.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;


public abstract class Acceptor extends Handler {
	
	static final Logger logger = Logger.getLogger("acceptor");
	
	private ServerSocketChannel channel;
	private boolean active;
	
	public Acceptor(SocketAddress addr) {
		try{
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.socket().bind(addr, 0);
			channel.socket().setReuseAddress(true);
			
			
			NetModel.register(channel, SelectionKey.OP_ACCEPT, this);
			active = true;
		}catch(IOException e){
			logger.warning(addr + " create err: " + e.getMessage());
			close();
		}
	}
	
	public boolean active() { return active; }

	public void close() { active = false; try{ if (null != channel) channel.close(); } catch(IOException ignored){} }
	
	public SocketAddress getLocalAddress() { return channel.socket().getLocalSocketAddress();  }
	
	@Override
	protected void doHandle() {
		if (!active) return;
		SocketChannel newconn = null; 
		try { newconn = channel.accept(); } catch (IOException e) {
			logger.fine(channel.socket().getLocalSocketAddress() + " accept err"); 
			logger.throwing("Acceptor", "doHandle", e); 
		}
		if ( null != newconn ) onAccept( newconn );
	}
	
	protected abstract void onAccept(SocketChannel newconnection);
	
}
