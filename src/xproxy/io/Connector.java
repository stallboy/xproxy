package xproxy.io;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;


public abstract class Connector extends Handler {

    static final Logger logger = Logger.getLogger("connector");

    private SocketChannel channel;
    private PollTask poll;
    private boolean active;
    private AtomicBoolean evented = new AtomicBoolean(false);

    public Connector(SocketAddress addr, PollTask poll, int timeout_secs) {
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.connect(addr);
            if (poll == null)
                poll = NetModel.register(channel, SelectionKey.OP_CONNECT, this);
            else
                poll.register(channel, SelectionKey.OP_CONNECT, this);
            this.poll = poll;
            active = true;

            NetModel.schedule(() -> event(false, true), timeout_secs, TimeUnit.SECONDS);

        } catch (IOException e) {
            event(false, false);
        }


    }

    public boolean active() {
        return active;
    }

    public void close() {
        active = false;
        try {
            if (null != channel) channel.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void doHandle() {
        if (!active) return;
        boolean conn_ok = false;
        if (key.isConnectable()) {
            try {
                conn_ok = channel.finishConnect();
            } catch (IOException e) {
                logger.throwing("Connector", "doHandle", e);
            }

            event(conn_ok, false);
        }
    }


    private void event(boolean ok, boolean timeout) {
        if (evented.getAndSet(true))
            return;

        if (ok)
            onConnectOk(channel, poll);
        else {
            close();
            onConnectFail(timeout);
        }
    }

    protected abstract void onConnectOk(SocketChannel channel, PollTask poll);

    protected abstract void onConnectFail(boolean timeout);

}
