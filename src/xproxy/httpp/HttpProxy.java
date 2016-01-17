package xproxy.httpp;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import xproxy.io.Acceptor;

public class HttpProxy extends Acceptor{

	static final Logger logger = Logger.getLogger("httpp");
	
	public HttpProxy(SocketAddress addr) {
		super(addr);
		
		if (active()) logger.info( addr + " inited" );
	}

	@Override
	protected void onAccept(SocketChannel newconnection) {
		new RequestXio(newconnection, this);
	}
	
	private List<Filter> filters = new LinkedList<>();
	
	public void addFilter(Filter filter) {
		filters.add(filter);
	}
	
	void doFilter(Request request, Response response){
		for(Filter f : filters)
			f.filter(request, response);
	}

}
