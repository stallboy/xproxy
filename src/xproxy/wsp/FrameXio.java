package xproxy.wsp;

import xproxy.io.Xio;
import xproxy.util.ByteBufferUtil;

import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FrameXio extends PipeXio {

    static final Logger logger = Logger.getLogger("FrameXio");

    private int step;
    private byte opcode;
    private long remainSize;
    private byte[] mask = new byte[4];
    private byte maskidx;

    private byte[] closedata;
    private byte[] pongdata;
    private boolean closing;
    boolean closed;

    public FrameXio(Xio prev) {
        super(prev);
    }

    @Override
    protected void onRecvBuf() {

        boolean wait = false;
        int len;
        while ((len = recvbuf.position()) > 0 && !wait) {
            switch (step) {
                case 0:
                    if (len < 6) {
                        wait = true;
                        break;
                    }
                    opcode = (byte) (recvbuf.get(0) & 0x0f);
                    byte plen = recvbuf.get(1);
                    assert ((plen & 0x80) != 0);
                    plen = (byte) (plen & 0x7f);
                    remainSize = plen;
                    int hsz = 2;
                    if (plen == 0x7f) {
                        if (len < 14) {
                            wait = true;
                            break;
                        }
                        remainSize = recvbuf.getLong(2);
                        hsz = 10;
                    } else if (plen == 0x7e) {
                        if (len < 8) {
                            wait = true;
                            break;
                        }
                        remainSize = recvbuf.getShort(2);
                        hsz = 4;
                    }

                    recvbuf.flip();
                    byte[] dummy = new byte[hsz];
                    recvbuf.get(dummy);
                    recvbuf.get(mask);
                    maskidx = 0;
                    recvbuf.compact();
                    step = opcode < 8 ? 1 : 2;
                    break;

                case 1:
                    long copylen = Math.min(Math.min(remainSize, len), pipeto.sendbuf.remaining());
                    byte[] bs = new byte[(int) copylen];
                    recvbuf.flip();
                    for (long i = 0; i < copylen; i++) {
                        byte b = (byte) (recvbuf.get() ^ mask[maskidx++]);
                        pipeto.sendbuf.put(b);
                        maskidx %= 4;
                        bs[(int) i] = b;
                    }
                    recvbuf.compact();

                    if (logger.isLoggable(Level.FINEST))
                        ByteBufferUtil.hexDump("from frameXio unmasked" + copylen, bs);

                    if (copylen < remainSize) {
                        remainSize -= copylen;
                        wait = true;
                        break;
                    }
                    step = 0;
                    break;

                case 2:
                    if (remainSize > len) {
                        wait = true;
                        break;
                    }
                    recvbuf.flip();
                    byte[] controldata = new byte[(int) remainSize];
                    recvbuf.get(controldata);
                    recvbuf.compact();
                    for (int i = 0; i < remainSize; i++) {
                        controldata[i] ^= mask[i % 4];
                    }
                    assert (controldata.length < 126);

                    if (opcode == 8) { //close
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                        closedata = controldata;
                        closing = true;
                    } else if (opcode == 9) { //ping
                        assert (!closing);
                        pongdata = controldata;
                        onSendBuf();
                    }
                    step = 0;
                    break;
            }
        }
    }

    @Override
    protected boolean onSendBuf() {
        if (closed) {
            if (sendbuf.position() == 0) {
                closeBoth();
                return false;
            }
            return true;
        }

        if (closing) {
            if (null != closedata && sendbuf.remaining() >= closedata.length + 2) {
                sendbuf.put((byte) 0x88);
                sendbuf.put((byte) closedata.length);
                sendbuf.put(closedata);

                closedata = null;
                closed = true;
            }

            return true;
        }

        if (null != pongdata && sendbuf.remaining() >= pongdata.length + 2) {
            sendbuf.put((byte) 0x8a);
            sendbuf.put((byte) pongdata.length);
            sendbuf.put(pongdata);
            pongdata = null;
        }
        return true;
    }

}
