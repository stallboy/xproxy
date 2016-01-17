package xproxy.socks;

import xproxy.io.Acceptor;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class SocksProxy extends Acceptor {

    static final Logger logger = Logger.getLogger("socks");

    public SocksProxy(SocketAddress addr) {
        super(addr);
        if (active()) logger.info(addr + " inited");
    }

    @Override
    protected void onAccept(SocketChannel channel) {
        new LocalXio(channel);
    }

}
