package xproxy.io;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;


public abstract class Xio extends Handler {

    protected SocketChannel channel;
    protected PollTask poll;
    private boolean active;


    public Xio(SocketChannel sc) {
        this(sc, null);
    }

    public Xio(SocketChannel sc, PollTask poll) {
        channel = sc;
        try {
            channel.configureBlocking(false);
            if (null == poll) this.poll = NetModel.register(sc.configureBlocking(false), SelectionKey.OP_READ, this);
            else {
                poll.register(sc.configureBlocking(false), SelectionKey.OP_READ, this);
                this.poll = poll;
            }
            active = true;
        } catch (IOException e) {
            close();
        }
    }

    public Xio(Xio prev) {
        channel = prev.channel;
        poll = prev.poll;
        active = prev.active;
        try {
            poll.register(channel, SelectionKey.OP_READ, this);
        } catch (IOException e) {
            close();
        }
    }

    public boolean active() {
        return active;
    }

    public void close() {
        active = false;
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    protected void doHandle() {
        if (!active) return;
        if (key.isReadable()) doRead();
        if (key.isWritable()) doWrite();
    }

    protected abstract void doRead();

    protected abstract void doWrite();

}
