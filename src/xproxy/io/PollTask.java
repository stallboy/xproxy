package xproxy.io;

import java.io.IOException;
import java.nio.channels.*;
import java.util.logging.Logger;


public class PollTask implements Runnable, Comparable<PollTask> {
    static final Logger logger = Logger.getLogger("poll");

    private Selector sel;

    int size() {
        return sel.keys().size();
    }

    public int compareTo(PollTask task) {
        return size() - task.size();
    }

    PollTask() throws IOException {
        this.sel = Selector.open();
    }

    public synchronized void register(SelectableChannel ch, int ops, Handler att) throws ClosedChannelException {
        att.setKey(ch.register(sel.wakeup(), ops, att));
    }

    void close() {
        for (SelectionKey sk : sel.keys()) {
            try {
                sk.channel().close();
            } catch (IOException ignored) {
            }
        }
        try {
            sel.close();
        } catch (IOException ignored) {
        }
    }

    public void run() {
        try {
            synchronized (this) {
            }
            sel.selectedKeys().clear();
            sel.select();
            for (SelectionKey key : sel.selectedKeys()) {
                if (!key.isValid()) continue;
                Handler io = (Handler) key.attachment();
                try {
                    io.doHandle();
                } catch (CancelledKeyException ignored) {
                } catch (Throwable e) {
                    logger.throwing("PollTask", "run1", e);
                    key.channel().close();
                }
            }

        } catch (Throwable e) {
            logger.throwing("PollTask", "run2", e);
        } finally {
            NetModel.pollPolicy.schedule(this);
        }
    }
}