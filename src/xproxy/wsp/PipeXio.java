package xproxy.wsp;

import xproxy.io.PollTask;
import xproxy.io.Xio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class PipeXio extends Xio {

    static final int BUF_SIZE = 1024 * 256;
    static final int MAX_FRAME_SIZE = 1024;
    static final Logger logger = Logger.getLogger("PipeXio");

    PipeXio pipeto;
    ByteBuffer sendbuf = ByteBuffer.allocateDirect(BUF_SIZE);
    ByteBuffer recvbuf = ByteBuffer.allocateDirect(BUF_SIZE);
    boolean eos;


    public PipeXio(SocketChannel sc, PollTask poll) {
        super(sc, poll);
    }

    public PipeXio(Xio prev) {
        super(prev);
    }

    protected boolean checkEnd() {
        return eos && pipeto.eos && sendbuf.position() == 0
                && recvbuf.position() == 0 && pipeto.recvbuf.position() == 0 && pipeto.sendbuf.position() == 0;
    }

    protected void closeBoth() {
        close();
        pipeto.close();
    }

    @Override
    protected void doRead() {
        int rn;
        try {
            rn = channel.read(recvbuf);
            eos = (rn == -1);
        } catch (IOException e) {
            logger.throwing("PipeXio", "doRead", e);
            closeBoth();
            return;
        }

        if (logger.isLoggable(Level.FINEST))
            logger.finest("recv" + rn + " " + channel.socket().getRemoteSocketAddress());

        if (checkEnd()) {
            closeBoth();
            return;
        }

        onRecvBuf();

        if (eos | !recvbuf.hasRemaining())
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);

        pipeto.checkEnableWrite();
    }


    @Override
    protected void doWrite() {
        int wn;
        try {
            sendbuf.flip();
            wn = channel.write(sendbuf);
            sendbuf.compact();

            if (pipeto.eos && sendbuf.position() == 0 && pipeto.recvbuf.position() == 0)
                channel.socket().shutdownOutput();
        } catch (IOException e) {
            logger.throwing("PipeXio", "doWrite", e);
            closeBoth();
            return;
        }

        if (logger.isLoggable(Level.FINEST))
            logger.finest("write" + wn + " " + channel.socket().getRemoteSocketAddress());

        if (checkEnd()) {
            closeBoth();
            return;
        }

        if (onSendBuf()) {
            if (sendbuf.position() == 0)
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);

            pipeto.checkEnableRead();
        }
    }

    void checkEnableWrite() {
        if (sendbuf.position() > 0)
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
    }

    void checkEnableRead() {
        if (!eos && (recvbuf.hasRemaining() || pipeto.sendbuf.hasRemaining()))
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
    }

    protected abstract void onRecvBuf();

    protected boolean onSendBuf() {
        return true;
    }


}
