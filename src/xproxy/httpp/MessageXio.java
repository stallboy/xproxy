package xproxy.httpp;

import xproxy.io.PollTask;
import xproxy.io.Xio;
import xproxy.util.ByteBufferUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class MessageXio extends Xio {

    static final Logger logger = Logger.getLogger("httpp");
    static final int INITIAL_BUF_SIZE = 1024 * 128;
    static final int MAX_BUF_SIZE = 1024 * 1024 * 128;

    protected ByteBuffer rbuf = ByteBuffer.allocateDirect(INITIAL_BUF_SIZE);
    protected boolean eos;
    private int step;
    private Message msg;
    private int content_length;
    private int chunk_size;
    private boolean is_request;

    public MessageXio(SocketChannel sc, PollTask poll, boolean isrequest) {
        super(sc, poll);
        is_request = isrequest;
    }

    private void autoResize() {
        int resize = 0;
        if (!rbuf.hasRemaining()) {
            resize = rbuf.capacity() * 2;
            if (resize > MAX_BUF_SIZE) {
                logger.warning(channel.socket().getRemoteSocketAddress() + " exceeded 128M");
                throw new RuntimeException("Exceeded 128M");
            }
        } else if (rbuf.capacity() > 2 * INITIAL_BUF_SIZE) {
            int len = rbuf.position();
            int round = INITIAL_BUF_SIZE;
            while (len > round) round <<= 1;
            if (rbuf.capacity() > round)
                resize = round;
        }
        if (resize > 0) {
            ByteBuffer old = rbuf;
            rbuf = ByteBuffer.allocateDirect(resize);
            old.flip();
            rbuf.put(old);
        }
    }

    @Override
    protected void doRead() {
        autoResize();

        int rn;
        try {
            rn = channel.read(rbuf);
            eos = (-1 == rn);
        } catch (IOException e) {
            close();
            return;
        }
        if (eos)
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        if (logger.isLoggable(Level.FINEST))
            logger.finest("recv" + rn + " " + channel.socket().getRemoteSocketAddress());

        boolean wait = false;
        int len;
        while ((len = rbuf.position()) > 0 && !wait) {

            switch (step) {
                case 0:
                    int idx = ByteBufferUtil.getCRLFCRLFIndex(rbuf);

                    if (idx == -1 || len < idx + 4) {
                        wait = true;
                        break;
                    }
                    byte[] head = new byte[idx];

                    rbuf.flip();
                    rbuf.get(head);
                    rbuf.getInt();
                    rbuf.compact();

                    String[] parts = new String(head).split("\r\n");
                    msg = is_request ? new Request() : new Response();
                    msg.setStartLine(parts[0]);
                    for (int i = 1; i < parts.length; i++) {
                        String[] p = parts[i].split(":", 2);
                        msg.getHeaders().put(p[0], p[1].trim());
                    }

                    switch (msg.getBodyType()) {
                        case EMPTY:
                            onMessage(msg);
                            break;

                        case BY_LENGTH:
                            content_length = Integer.parseInt(msg.getHeaders().get(Message.CONTENT_LENGTH));
                            step = 1;
                            break;

                        case CHUNKED:
                            step = 2;
                            break;

                        case MULTIPART:
                            logger.warning("multipart not implemented");
                            close();
                            break;

                        case BY_CLOSE:
                            step = 6;
                            break;
                    }
                    break;

                case 1:
                    if (len < content_length) {
                        wait = true;
                        break;
                    }
                    byte[] body = new byte[content_length];

                    rbuf.flip();
                    rbuf.get(body);
                    rbuf.compact();

                    msg.setBody(body);
                    onMessage(msg);
                    step = 0;
                    break;

                case 2:
                    idx = ByteBufferUtil.getCRLFIndex(rbuf);
                    if (idx == -1 || len < idx + 2) {
                        wait = true;
                        break;
                    }
                    head = new byte[idx];

                    rbuf.flip();
                    rbuf.get(head);
                    rbuf.getShort();
                    rbuf.compact();

                    parts = new String(head).split(" ", 2);
                    chunk_size = Integer.parseInt(parts[0], 16);

                    step = chunk_size != 0 ? 3 : 4;
                    break;

                case 3:
                    if (len < chunk_size + 2) {
                        wait = true;
                        break;
                    }
                    byte[] chunk_data = new byte[chunk_size];

                    rbuf.flip();
                    rbuf.get(chunk_data);
                    rbuf.getShort();
                    rbuf.compact();

                    msg.addChunk(chunk_data);
                    step = 2;
                    break;

                case 4:
                    idx = ByteBufferUtil.getCRLFIndex(rbuf);
                    if (idx == -1) {
                        wait = true;
                        break;
                    }
                    if (idx == 0) {

                        rbuf.flip();
                        rbuf.getShort();
                        rbuf.compact();

                        onMessage(msg);
                        step = 0;
                    } else {
                        step = 5;
                    }
                    break;

                case 5:
                    idx = ByteBufferUtil.getCRLFCRLFIndex(rbuf);
                    if (idx == -1 || len < idx + 4) {
                        wait = true;
                        break;
                    }
                    head = new byte[idx];

                    rbuf.flip();
                    rbuf.get(head);
                    rbuf.getInt();
                    rbuf.compact();

                    parts = new String(head).split("\r\n");
                    for (int i = 1; i < parts.length; i++) {
                        String[] p = parts[i].split(":", 2);
                        msg.getHeaders().put(p[0], p[1].trim());
                    }

                    onMessage(msg);
                    step = 0;
                    break;

                case 6:
                    if (!eos) {
                        //wait = true;
                        return;
                    }
                    body = new byte[len];

                    rbuf.flip();
                    rbuf.get(body);
                    rbuf.compact();

                    msg.setBody(body);
                    onMessage(msg);
                    step = -1;
                    break;
            }
        }
    }


    protected abstract void onMessage(Message msg);

}
