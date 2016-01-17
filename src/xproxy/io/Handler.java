package xproxy.io;

import java.nio.channels.SelectionKey;

public abstract class Handler {
    protected SelectionKey key;

    void setKey(SelectionKey k) {
        key = k;
    }

    protected abstract void doHandle();
}
