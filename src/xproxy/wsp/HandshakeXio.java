package xproxy.wsp;

import xproxy.httpp.Message;
import xproxy.httpp.MessageXio;
import xproxy.httpp.Request;
import xproxy.httpp.Response;
import xproxy.io.Connector;
import xproxy.io.PollTask;
import xproxy.util.EncryptUtil;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class HandshakeXio extends MessageXio {

    private WebSocketProxy ws;
    private SocketChannel destChannel;
    private Connector destConnector;
    private ByteBuffer tobesend;
    private boolean shakeOk;

    public HandshakeXio(SocketChannel sc, WebSocketProxy ws) {
        super(sc, null, true);
        this.ws = ws;
    }

    static private boolean isContains(String header, String val) {
        if (null == header)
            return false;
        String[] v = header.trim().split("\\s*,\\s*");
        for (String aV : v) {
            if (val.equalsIgnoreCase(aV))
                return true;
        }
        return false;
    }


    @Override
    protected void onMessage(Message msg) {
        Request req = Request.fromMessage(msg);


        if (req.getMethod().equalsIgnoreCase("GET") && req.getVersion().equalsIgnoreCase("HTTP/1.1")) {

            final SocketAddress sa = ws.resource2socket.get(req.getRequestUrl());

            if (sa != null && ws.hostname.equalsIgnoreCase(req.getHeaders().get("Host").trim().split(":")[0]) &&
                    isContains(req.getHeaders().get("Upgrade"), "websocket") &&
                    isContains(req.getHeaders().get("Connection"), "Upgrade") &&
                    "13".equalsIgnoreCase(req.getHeaders().get("Sec-WebSocket-Version").trim())) {

                String origin = req.getHeaders().get("Origin");
                String seckey = req.getHeaders().get("Sec-WebSocket-Key");

                if (null != origin && null != seckey) {
                    byte[] decodedKey = EncryptUtil.base64Decode(seckey);
                    if (null != decodedKey && decodedKey.length == 16) {
                        String key = seckey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                        final byte[] sha = EncryptUtil.sha1Digest(key);
                        if (null != sha) {

                            new Connector(sa, poll, 60) {

                                @Override
                                protected void onConnectOk(SocketChannel channel, PollTask poll) {
                                    Response res = new Response();
                                    String accept = EncryptUtil.base64Encode(sha);
                                    res.setStartLine("HTTP/1.1 101 Switching Protocols");
                                    res.getHeaders().put("Upgrade", "WebSocket");
                                    res.getHeaders().put("Connection", "Upgrade");
                                    res.getHeaders().put("Sec-WebSocket-Accept", accept);
                                    res.getHeaders().put("Server", "xproxy");

                                    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                                    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                                    res.getHeaders().put("Date", dateFormat.format(Calendar.getInstance().getTime()));

                                    destChannel = channel;
                                    destConnector = this;
                                    replyOk(res);
                                }

                                @Override
                                protected void onConnectFail(boolean timeout) {
                                    if (timeout)
                                        replyError("HTTP/1.1 504 Gateway Timeout");
                                    else
                                        replyError("HTTP/1.1 503 Service Unavailable");
                                }
                            };

                        } else {
                            replyError("HTTP/1.1 500 Internal Server Error");
                        }
                        return;
                    }
                }
            }
        }

        replyError("HTTP/1.1 400 Bad Request");
    }

    @Override
    protected void doWrite() {
        if (tobesend == null)
            return;

        try {
            tobesend.flip();
            channel.write(tobesend);
            tobesend.compact();

            if (tobesend.position() == 0) {
                if (shakeOk) {
                    DestXio dst = new DestXio(destChannel, poll);
                    FrameXio frm = new FrameXio(HandshakeXio.this);

                    dst.pipeto = frm;
                    frm.pipeto = dst;
                } else {
                    close();
                }
            }

        } catch (IOException e) {
            close();
            if (null != destConnector)
                destConnector.close();
        }
    }

    private void replyError(String statusline) {
        Response res = new Response();
        res.setStartLine(statusline);

        tobesend = res.toByteBuffer();
        key.interestOps(SelectionKey.OP_WRITE);
        shakeOk = false;
    }

    private void replyOk(Response res) {
        tobesend = res.toByteBuffer();
        key.interestOps(SelectionKey.OP_WRITE);
        shakeOk = true;
    }
}
