package xproxy.wsp;

import xproxy.io.Acceptor;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.logging.Logger;


public class WebSocketProxy extends Acceptor {

    static final Logger logger = Logger.getLogger("wsp");

    String hostname;
    Map<String, SocketAddress> resource2socket;

    public WebSocketProxy(SocketAddress localaddr, String hostname, Map<String, SocketAddress> res2socket) {
        super(localaddr);
        this.hostname = hostname;
        this.resource2socket = res2socket;

        if (active()) {
            StringBuilder sb = new StringBuilder();
            res2socket.forEach((k, v) -> sb.append(k).append(":").append(v).append(","));
            sb.deleteCharAt(sb.length() - 1);
            logger.info(localaddr + " -> [" + sb + "] inited");
        }
    }

    @Override
    protected void onAccept(SocketChannel newconnection) {
        new HandshakeXio(newconnection, this);
    }

}
