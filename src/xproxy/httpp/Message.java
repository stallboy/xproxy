package xproxy.httpp;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public abstract class Message {
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String CONTENT_TYPE = "Content-Type";


    public enum BodyType {EMPTY, BY_LENGTH, CHUNKED, MULTIPART, BY_CLOSE}

    protected String startline;
    protected Map<String, String> headers = new HashMap<>();
    protected byte[] body;

    private BodyType bodytype;
    private Queue<ByteBuffer> chunks = new LinkedList<>();


    public String getStartLine() {
        return startline;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }


    public byte[] getBody() {
        if (body != null) return body;

        int bodylen = 0;
        for (ByteBuffer c : chunks)
            bodylen += c.capacity();
        body = new byte[bodylen];
        int idx = 0;
        for (ByteBuffer c : chunks) {
            c.get(body, idx, c.capacity());
            idx += c.capacity();
            c.flip();
        }
        chunks.clear();

        return body;
    }

    public BodyType getBodyType() {
        if (bodytype == null)
            bodytype = findBodyType();
        return bodytype;
    }

    public void setStartLine(String line) {
        startline = line;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public void addChunk(byte[] chunk) {
        chunks.offer(ByteBuffer.wrap(chunk));
    }

    public void dumpHeaders(PrintStream ps) {
        ps.println(startline);
        for (Map.Entry<String, String> e : headers.entrySet())
            ps.println(e.getKey() + ": " + e.getValue());
        ps.println();
    }

    public ByteBuffer toByteBuffer() {
        byte[] bd = getBody();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        switch (getBodyType()) {
            case CHUNKED:
                Map<String, String> heads = new HashMap<>(headers);
                heads.remove(TRANSFER_ENCODING);
                heads.put(CONTENT_LENGTH, String.valueOf(bd.length));

                ps.println(startline);
                for (Map.Entry<String, String> e : heads.entrySet())
                    ps.println(e.getKey() + ": " + e.getValue());
                ps.println();
                break;
            default:
                dumpHeaders(ps);
                break;
        }
        ps.close();

        byte[] bs = baos.toByteArray();

        ByteBuffer buf = ByteBuffer.allocate(bs.length + bd.length);
        buf.put(bs).put(bd);
        return buf;
    }

    protected abstract BodyType findBodyType();
}
