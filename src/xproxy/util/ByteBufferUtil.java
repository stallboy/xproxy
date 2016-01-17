package xproxy.util;

import java.io.BufferedOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;


public class ByteBufferUtil {

    public static int getCRLFCRLFIndex(ByteBuffer buffer) {
        int len = buffer.position();
        int s = 0;
        for (int i = 0; i < len; i++) {
            switch (buffer.get(i)) {
                case '\r':
                    switch (s) {
                        case 0:
                            s = 1;
                            break;
                        case 2:
                            s = 3;
                            break;
                        case 3:
                            s = 1;
                            break;
                    }
                    break;
                case '\n':
                    switch (s) {
                        case 1:
                            s = 2;
                            break;
                        case 2:
                            s = 0;
                            break;
                        case 3:
                            return i - 3;
                    }
                    break;
                default:
                    s = 0;
                    break;
            }
        }
        return -1;
    }

    public static int getCRLFIndex(ByteBuffer buffer) {
        int len = buffer.position();
        int s = 0;
        for (int i = 0; i < len; i++) {
            switch (buffer.get(i)) {
                case '\r':
                    s = 1;
                    break;
                case '\n':
                    if (s == 1) return i - 1;
                    break;
                default:
                    s = 0;
                    break;
            }
        }
        return -1;
    }

    public static void dump(ByteBuffer buffer) {
        ByteBuffer dup = buffer.duplicate();
        byte[] bs = new byte[dup.position()];
        dup.flip();
        dup.get(bs);
        System.out.println(new String(bs));
    }

    public static void hexDump(String prompt, byte[] bs) {
        PrintStream ps = new PrintStream(new BufferedOutputStream(System.out, 2048));

        ps.printf("%s:", prompt);

        for (byte b : bs) ps.printf("%02x ", b);
        ps.printf("[");
        for (byte b : bs) ps.printf("%c", Character.isLetterOrDigit(b) ? b : '.');
        ps.printf("]\n");

        ps.flush();
    }
}
