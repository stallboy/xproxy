package xproxy.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LogFormatter extends Formatter {

    private final static String format = "{0,date} {0,time}";

    @Override
    public String format(LogRecord record) {

        StringBuilder sb = new StringBuilder();

        Date dat = new Date();
        dat.setTime(record.getMillis());
        sb.append(MessageFormat.format(format, dat));

        sb.append(" ").append(record.getLoggerName()).append(" ");
        sb.append(record.getLevel().getName());

        sb.append(": ");
        sb.append(formatMessage(record));
        sb.append("\n");

        if (record.getThrown() != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            } catch (Exception ignored) {
            }
        }
        return sb.toString();
    }

}