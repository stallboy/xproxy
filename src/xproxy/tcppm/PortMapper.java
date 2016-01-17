package xproxy.tcppm;

import xproxy.io.Acceptor;
import xproxy.io.Connector;
import xproxy.io.Exchanger;
import xproxy.io.PollTask;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

public class PortMapper extends Acceptor {
    static final Logger logger = Logger.getLogger("tcppm");

    private SocketAddress ra;

    public PortMapper(SocketAddress localaddr, SocketAddress remoteaddr) {
        super(localaddr);
        ra = remoteaddr;

        if (active()) logger.info(localaddr + " -> " + remoteaddr + " inited");
    }

    @Override
    protected void onAccept(final SocketChannel lc) {

        new Connector(ra, null, 120) {

            @Override
            protected void onConnectOk(SocketChannel rc, PollTask poll) {
                new Exchanger(lc, rc, null);
            }

            @Override
            protected void onConnectFail(boolean timeout) {
                try {
                    lc.close();
                } catch (Throwable ignored) {
                }
            }

        };

    }

}