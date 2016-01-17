package xproxy.httpp;

import xproxy.io.Connector;
import xproxy.io.PollTask;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestXio extends MessageXio {

    static final Logger logger = Logger.getLogger("httpp");
    static private int seq = 1;

    private List<ResponseConnector> tasks = new LinkedList<>();
    private Queue<ByteBuffer> vbuf = new LinkedList<>();
    private HttpProxy proxy;
    private String ident;


    public RequestXio(SocketChannel sc, HttpProxy proxy) {
        super(sc, null, true);
        this.proxy = proxy;
        ident = String.valueOf(((InetSocketAddress) sc.socket().getRemoteSocketAddress()).getPort());
        ident += "-" + seq++;
    }

    @Override
    protected void doWrite() {
        long wn;
        try {
            wn = channel.write(vbuf.toArray(new ByteBuffer[vbuf.size()]));
            while (!vbuf.isEmpty() && !vbuf.peek().hasRemaining())
                vbuf.poll();
            if (vbuf.isEmpty()) {
                if (eos && tasks.isEmpty()) {
                    closeAll();
                }

                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            }
            if (logger.isLoggable(Level.FINER))
                logger.finer(ident + " sendin " + wn);
        } catch (IOException e) {
            closeAll();
        }
    }

    @Override
    protected void onMessage(Message msg) {
        String[] host = msg.getHeaders().get("Host").split(":");
        int port = host.length < 2 ? 80 : Integer.parseInt(host[1]);
        tasks.add(new ResponseConnector(new InetSocketAddress(host[0], port), poll, Request.fromMessage(msg)));
    }

    void onResponse(ResponseConnector conn) {
        proxy.doFilter(conn.request, conn.response);
        if (logger.isLoggable(Level.FINE))
            logger.fine(ident + " fetched " + conn.request.getRequestUrl() + " " + conn.response.getStatusCode());

        int i = 0;
        for (ResponseConnector r : tasks) {
            if (null == r.response) break;
            ByteBuffer b = r.response.toByteBuffer();
            b.flip();
            vbuf.offer(b);
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            i++;
        }

        tasks = tasks.subList(i, tasks.size());
    }

    void closeAll() {
        close();
        tasks.forEach(ResponseConnector::close);
    }


    class ResponseConnector extends Connector {

        Request request;
        Response response;

        ResponseConnector(SocketAddress addr, PollTask poll, Request req) {
            super(addr, poll, 120);
            request = req;
            if (logger.isLoggable(Level.FINE))
                logger.fine(ident + " connect " + request.getRequestUrl());
        }

        @Override
        protected void onConnectOk(SocketChannel channel, PollTask poll) {
            new ResponseXio(channel, poll);
        }

        @Override
        protected void onConnectFail(boolean timeout) {
            RequestXio.this.closeAll();
        }


        class ResponseXio extends MessageXio {
            private ByteBuffer tobesend;

            ResponseXio(SocketChannel sc, PollTask poll) {
                super(sc, poll, false);
                if (!active()) {
                    RequestXio.this.closeAll();
                    return;
                }
                tobesend = request.toByteBuffer();
                key.interestOps(SelectionKey.OP_WRITE);
            }


            @Override
            protected void doWrite() {
                try {
                    tobesend.flip();
                    channel.write(tobesend);
                    tobesend.compact();

                    if (tobesend.position() == 0) {
                        key.interestOps(SelectionKey.OP_READ);
                        if (logger.isLoggable(Level.FINER))
                            logger.finer(ident + " sendout " + request.getRequestUrl());
                    }

                } catch (IOException e) {
                    RequestXio.this.closeAll();
                }
            }

            @Override
            protected void onMessage(Message msg) {
                response = Response.fromMessage(msg);
                close();
                RequestXio.this.onResponse(ResponseConnector.this);
            }
        }

    }


}
