package xproxy.wsp;

import xproxy.io.PollTask;
import xproxy.util.ByteBufferUtil;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

public class DestXio extends PipeXio {

    static final int MAX_FRAME_SIZE = 1024;


    public DestXio(SocketChannel sc, PollTask poll) {
        super(sc, poll);
    }

    @Override
    protected void onRecvBuf() {

        FrameXio frame = (FrameXio) pipeto;
        if (frame.closed) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            return;
        }

        int len = recvbuf.position();
        int remain = pipeto.sendbuf.remaining();
        while (remain > 4 && len > 0) {
            int fs = Math.min(MAX_FRAME_SIZE, Math.min(remain - 4, len));
            if (fs < 125) {
                fs = Math.min(125, Math.min(remain - 2, len));
            }

            pipeto.sendbuf.put((byte) 0x82);
            if (fs > 125) {
                pipeto.sendbuf.put((byte) 0x7e);
                pipeto.sendbuf.putShort((short) fs);
            } else
                pipeto.sendbuf.put((byte) fs);

            byte[] bs = new byte[fs];
            recvbuf.flip();
            for (int i = 0; i < fs; i++) {
                byte b = recvbuf.get();
                pipeto.sendbuf.put(b);
                bs[i] = b;
            }
            recvbuf.compact();

            if (logger.isLoggable(Level.FINEST))
                ByteBufferUtil.hexDump("from DestXio" + fs, bs);

            len = recvbuf.position();
            remain = pipeto.sendbuf.remaining();
        }

    }

    @Override
    void checkEnableRead() {
        if (!eos && (recvbuf.hasRemaining() || pipeto.sendbuf.remaining() > 4))
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
    }


}
