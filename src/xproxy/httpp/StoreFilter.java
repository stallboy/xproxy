package xproxy.httpp;

import xproxy.httpp.Message.BodyType;

import java.io.*;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class StoreFilter implements Filter {

    static final Logger logger = Logger.getLogger("httpp");
    static final ExecutorService store = Executors.newFixedThreadPool(1, r -> {
        Thread thr = new Thread(r);
        thr.setName("StoreService-" + thr.getId());
        return thr;
    });

    private File parent;
    private final FileOutputStream urls;
    private final FileOutputStream stores;
    private final FileOutputStream headers;

    public StoreFilter(String basedir) throws IOException {
        parent = new File(basedir);
        parent.mkdirs();
        urls = new FileOutputStream(new File(parent, "urls"));
        stores = new FileOutputStream(new File(parent, "stores"), true);
        headers = new FileOutputStream(new File(parent, "headers"));
    }

    @Override
    public void filter(final Request request, final Response response) {

        final String time = MessageFormat.format("{0,date} {0,time}", new Date());

        store.execute(() -> {
            try {
                urls.write((time + " " + request.getRequestUrl() + " " + response.getStatusCode() + "\n").getBytes());
                urls.flush();

                headers.write("=========================================\n".getBytes());
                headers.write((time + "\n\n").getBytes());
                PrintStream ps = new PrintStream(headers);
                request.dumpHeaders(ps);
                response.dumpHeaders(ps);
                headers.flush();

                if (response.getBodyType() == BodyType.EMPTY) return;
                String host = request.getHeaders().get("Host");
                URL url = new URL(request.getRequestUrl());
                File f = new File(new File(parent, host), url.getPath());

                if (f.getPath().endsWith("\\") || f.getPath().endsWith("/")) {
                    f.mkdirs();
                    f = new File(f, "index.html");
                } else {
                    f.getParentFile().mkdirs();
                }
                FileOutputStream fos = new FileOutputStream(f);

                boolean stored = true;
                String code = response.getHeaders().get("Content-Encoding");
                if (code == null || code.equals("identity")) {
                    fos.write(response.getBody());

                } else if (code.equals("gzip") || code.equals("x-gzip")) {
                    GZIPInputStream zis = new GZIPInputStream(new ByteArrayInputStream(response.getBody()));
                    int b;
                    while ((b = zis.read()) != -1)
                        fos.write(b);
                    zis.close();

                } else if (code.equals("compress") || code.equals("x-compress")) {
                    stores.write((time + " compress not supported " + f.getAbsolutePath() + "\n").getBytes());
                    stores.flush();
                    stored = false;
                } else if (code.equals("deflate")) {
                    InflaterInputStream zis = new InflaterInputStream(new ByteArrayInputStream(response.getBody()));
                    int b;
                    while ((b = zis.read()) != -1)
                        fos.write(b);
                    zis.close();

                }
                if (stored) {
                    stores.write((time + " " + f.getAbsolutePath() + "\n").getBytes());
                    stores.flush();
                }

                fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        });

    }

}
