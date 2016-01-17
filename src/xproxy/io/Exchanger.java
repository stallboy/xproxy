package xproxy.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Exchanger {
    static final int BUF_SIZE = 1024 * 256;
    static final Logger logger = Logger.getLogger("exchanger");

    private Pipeio lc;
    private Pipeio rc;
    private boolean active;
    private String eident;

    public Exchanger(SocketChannel local, SocketChannel remote, byte[] localPrefix) {
        lc = new Pipeio(local, localPrefix);
        if (lc.active()) {
            rc = new Pipeio(remote, null);
            if (rc.active()) {
                lc.other = rc;
                rc.other = lc;
                eident = String.format("%s -> %s", local.socket().getRemoteSocketAddress(), remote.socket().getRemoteSocketAddress());
                lc.ident = eident + " L";
                rc.ident = eident + " R";

                active = true;
                if (logger.isLoggable(Level.FINE))
                    logger.fine(this + " linked");
            } else {
                lc.close();
            }
        }
    }

    public boolean active() {
        return active;
    }

    public void close() {
        active = false;
        lc.close();
        rc.close();
    }

    @Override
    public String toString() {
        return eident;
    }

    private String status() {
        return String.format("tobesend L%d:R%d", lc.tobesend.position(), rc.tobesend.position());
    }


    class Pipeio extends Xio {

        private ByteBuffer tobesend = ByteBuffer.allocateDirect(BUF_SIZE);
        private boolean eos = false;
        private Pipeio other;
        private String ident;

        Pipeio(SocketChannel channel, byte[] prefix) {
            super(channel);
            if (null != prefix && prefix.length > 0) {
                tobesend.put(prefix);
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }

        @Override
        public String toString() {
            return ident;
        }

        private boolean checkEnd() {
            return eos && other.eos && tobesend.position() == 0 && other.tobesend.position() == 0;
        }

        private void closeBoth(int errno) {
            if (logger.isLoggable(Level.FINE))
                logger.fine(this + " closed" + errno);
            Exchanger.this.close();
        }

        @Override
        protected void doRead() {
            try {
                int rn = channel.read(other.tobesend);
                if (logger.isLoggable(Level.FINER))
                    logger.finer(this + " recv " + rn + " " + Exchanger.this.status());

                if (eos = (rn == -1)) {
                    if (other.tobesend.position() == 0)
                        other.channel.socket().shutdownOutput();
                    if (logger.isLoggable(Level.FINE))
                        logger.fine(this + " EOS");
                }
            } catch (IOException e) {
                logger.throwing("Exchanger", "doRead", e);
                closeBoth(1);
                return;
            }
            if (checkEnd()) {
                closeBoth(0);
                return;
            }

            if (eos || !other.tobesend.hasRemaining())
                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            if (other.tobesend.position() > 0)
                other.key.interestOps(other.key.interestOps() | SelectionKey.OP_WRITE);

        }

        @Override
        protected void doWrite() {
            try {
                tobesend.flip();
                int wn = channel.write(tobesend);
                tobesend.compact();

                if (logger.isLoggable(Level.FINER))
                    logger.finer(this + " send " + wn + " " + Exchanger.this.status());

                if (other.eos && tobesend.position() == 0)
                    channel.socket().shutdownOutput();
            } catch (IOException e) {
                logger.throwing("Exchanger", "doWrite", e);
                closeBoth(2);
                return;
            }

            if (checkEnd()) {
                closeBoth(0);
                return;
            }

            if (tobesend.position() == 0)
                key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
            if (!other.eos && tobesend.hasRemaining())
                other.key.interestOps(other.key.interestOps() | SelectionKey.OP_READ);

        }

    }

}
