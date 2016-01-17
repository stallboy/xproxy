package xproxy.httpp;

public final class Response extends Message {

    private String code;

    public String getStatusLine() {
        return startline;
    }

    public String getStatusCode() {
        return code;
    }

    public static Response fromMessage(Message msg) {
        Response res = new Response();
        res.setStartLine(msg.startline);
        res.headers = msg.headers;
        res.body = msg.getBody();
        return res;
    }

    @Override
    public void setStartLine(String line) {
        startline = line;
        code = startline.split(" ")[1];
    }

    @Override
    protected BodyType findBodyType() {
        if (code.startsWith("1") || code.equals("204") || code.equals("304"))
            return BodyType.EMPTY;

        String tf = headers.get(TRANSFER_ENCODING);
        String cl = headers.get(CONTENT_LENGTH);
        String ct = headers.get(CONTENT_TYPE);

        if (tf != null && !tf.equals("identity"))
            return BodyType.CHUNKED;
        else if (cl != null && !cl.equals("0"))
            return BodyType.BY_LENGTH;
        else if (ct != null && ct.startsWith("multipart/byteranges"))
            return BodyType.MULTIPART;

        return BodyType.BY_CLOSE;
    }
}
