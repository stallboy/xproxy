package xproxy.socks;

import xproxy.io.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalXio extends Xio {

    private static final byte VER = 5;
    private static final byte RSV = 0;

    private static final byte METHOD_NOAUTH = 0;

    private static final byte CMD_CONNECT = 1;
    private static final byte CMD_BIND = 2;
    private static final byte CMD_UDPASSOCIATE = 3;

    private static final byte ATYP_IPV4 = 1;
    private static final byte ATYP_DOMAINNAME = 3;
    private static final byte ATYP_IPV6 = 4;

    private static final byte REP_SUCCESS = 0;
    private static final byte REP_FAIL = 1;
    private static final byte REP_HOST_UNREACHABLE = 4;
    private static final byte REP_CONNECTION_REFUSED = 5;
    private static final byte REP_TTL_EXPIRED = 6;
    private static final byte REP_COMMAND_NOT_SUPPORTED = 7;
    private static final byte REP_ADDRESS_TYPE_NOT_SUPPORTED = 8;


    private ByteBuffer recvbuf = ByteBuffer.allocateDirect(512); //������buffer�϶������Ͳ�������������
    private ByteBuffer sendbuf = ByteBuffer.allocateDirect(512);
    private boolean eos;
    private int step;

    private byte atyp;
    private byte[] dst_addr;
    private int dst_port;

    private PollTask poll;

    LocalXio(SocketChannel sc) {
        super(sc);
    }

    private void replyError(byte rep) {
        sendbuf.put(VER).put(rep).put(RSV).put(atyp);

        if (dst_addr != null) {
            if (atyp == ATYP_DOMAINNAME)
                sendbuf.put((byte) dst_addr.length);
            sendbuf.put(dst_addr);
        } else {
            sendbuf.put((byte) 0);
        }
        sendbuf.putShort((short) dst_port);
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

        NetModel.schedule(this::close, 3, TimeUnit.SECONDS);
    }

    private byte[] replyOk(InetAddress addr, int port) {
        ByteBuffer buf = ByteBuffer.allocateDirect(512);
        buf.put(VER).put(REP_SUCCESS).put(RSV);
        byte[] ads = addr.getAddress();
        switch (ads.length) {
            case 4:
                buf.put(ATYP_IPV4).put(ads).putShort((short) port);
                break;
            case 16:
                buf.put(ATYP_IPV6).put(ads).putShort((short) port);
                break;
            default:
                buf.put(ATYP_DOMAINNAME).put((byte) 0).putShort((short) port);
        }
        byte[] r = new byte[buf.position()];

        buf.flip();
        buf.get(r);

        return r;
    }

    protected void doRead() {
        try {
            eos = (-1 == channel.read(recvbuf));
        } catch (IOException e) {
            close();
            return;
        }
        if (eos) {
            if (sendbuf.position() == 0) {
                close();
                return;
            }
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }

        int len = recvbuf.position();

        switch (step) {
            case 0:
                if (len < 2) return;

                byte version = recvbuf.get(0);
                if (version != 5) {
                    close();
                    return;
                }

                byte nmethods = recvbuf.get(1);
                if (len < 2 + nmethods) return;

                recvbuf.flip();
                recvbuf.getShort();
                byte[] methods = new byte[nmethods];
                recvbuf.get(methods);
                recvbuf.compact();

                sendbuf.put(VER).put(METHOD_NOAUTH);

                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                step = 1;
                break;

            case 2:
                if (len < 7) return;
                byte ver = recvbuf.get(0);
                if (ver != 5) {
                    close();
                    return;
                }

                byte cmd = recvbuf.get(1);
                atyp = recvbuf.get(3);

                byte rep = cmd != CMD_CONNECT && cmd != CMD_BIND && cmd != CMD_UDPASSOCIATE ? REP_COMMAND_NOT_SUPPORTED : REP_SUCCESS;

                int dst_addr_len = 0;
                switch (atyp) {
                    case ATYP_IPV4:
                        dst_addr_len = 4;
                        break;
                    case ATYP_DOMAINNAME:
                        dst_addr_len = (0xFF & recvbuf.get(4)) + 1;
                        if (dst_addr_len > 256) rep = REP_FAIL;
                        break;
                    case ATYP_IPV6:
                        dst_addr_len = 16;
                        break;
                    default:
                        rep = REP_ADDRESS_TYPE_NOT_SUPPORTED;
                        break;
                }

                if (rep != REP_SUCCESS) {
                    replyError(rep);
                    return;
                }

                if (len < dst_addr_len + 6) return;

                recvbuf.flip();
                recvbuf.getInt();
                dst_addr = new byte[dst_addr_len];
                recvbuf.get(dst_addr);
                dst_port = 0xFFFF & recvbuf.getShort();
                recvbuf.compact();

                switch (cmd) {
                    case CMD_CONNECT:
                        try {
                            InetAddress addr = atyp == ATYP_DOMAINNAME ? InetAddress.getByName(new String(dst_addr)) : InetAddress.getByAddress(dst_addr);
                            new Connector(new InetSocketAddress(addr, dst_port), poll, 120) {

                                @Override
                                protected void onConnectOk(SocketChannel sc, PollTask poll) {
                                    new Exchanger(LocalXio.this.channel, sc, replyOk(sc.socket().getLocalAddress(), sc.socket().getLocalPort()));
                                }

                                @Override
                                protected void onConnectFail(boolean timeout) {
                                    replyError(timeout ? REP_TTL_EXPIRED : REP_CONNECTION_REFUSED);
                                }

                            };
                        } catch (UnknownHostException uk) {
                            replyError(REP_HOST_UNREACHABLE);
                        }

                        break;

                    case CMD_BIND:
                        final InetSocketAddress sa = new InetSocketAddress(0);
                        final AtomicBoolean accepted = new AtomicBoolean(false);
                        final Acceptor acc = new Acceptor(sa) {

                            @Override
                            protected void onAccept(SocketChannel newconnection) {
                                if (accepted.getAndSet(true))
                                    return;

                                InetSocketAddress ra = (InetSocketAddress) newconnection.socket().getRemoteSocketAddress();

                                if (ra.getAddress().equals(sa.getAddress()))
                                    new Exchanger(LocalXio.this.channel, newconnection, replyOk(ra.getAddress(), ra.getPort()));
                                else
                                    replyError(REP_CONNECTION_REFUSED);

                                close();
                            }

                        };

                        if (acc.active()) {
                            InetSocketAddress addr = (InetSocketAddress) acc.getLocalAddress();
                            if (addr == null) {
                                replyError(REP_FAIL);
                                acc.close();
                            } else {
                                sendbuf.put(replyOk(addr.getAddress(), addr.getPort()));
                                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

                                NetModel.schedule(() -> {
                                    if (accepted.getAndSet(true))
                                        return;

                                    acc.close();
                                    replyError(REP_TTL_EXPIRED);
                                }, 120, TimeUnit.SECONDS);
                            }
                        } else {
                            replyError(REP_FAIL);
                        }

                        break;
                    case CMD_UDPASSOCIATE:
                        SocksProxy.logger.warning("udp associate !!! not implemented");
                        replyError(REP_COMMAND_NOT_SUPPORTED);
                        break;
                }

                step = 3;
                break;
        }
    }

    protected void doWrite() {
        try {
            sendbuf.flip();
            channel.write(sendbuf);
            sendbuf.compact();
        } catch (IOException e) {
            SocksProxy.logger.throwing("LocalSession", "doWrite", e);
            close();
            return;
        }

        if (sendbuf.position() == 0) {
            if (eos) {
                close();
                return;
            } else key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }

        switch (step) {
            case 1:
                if (sendbuf.position() == 0)
                    step = 2;
                break;
        }
    }

}
