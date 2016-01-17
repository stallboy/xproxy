package xproxy.util;

import java.io.IOException;
import java.security.MessageDigest;

public class EncryptUtil {

    public static String base64Encode(byte[] bstr) {
        return new sun.misc.BASE64Encoder().encode(bstr);
    }

    public static byte[] base64Decode(String str) {
        byte[] bt = null;
        try {
            sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
            bt = decoder.decodeBuffer(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bt;
    }

    public static byte[] sha1Digest(String src) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return md.digest(src.getBytes("UTF8"));
        } catch (Exception e) {
            return null;
        }
    }

}
