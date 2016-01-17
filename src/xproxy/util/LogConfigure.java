package xproxy.util;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Properties;
import java.util.logging.LogManager;

public class LogConfigure {

    private static String _pattern;
    private static String _level;
    private static int _limit;
    private static int _count;

    public static void initialize(String level, String pattern) {
        initialize(level, pattern, 256 * 1024 * 1024, 10);
    }

    public static void initialize(String level, String pattern, int limit, int count) {
        _level = level;
        _pattern = pattern;
        _limit = limit;
        _count = count;

        System.setProperty("java.util.logging.config.class", "xproxy.util.LogConfigure");
    }

    public LogConfigure() throws Exception {
        Properties props = new Properties();

        props.put(".level", "ALL");
        props.setProperty("handlers", "java.util.logging.FileHandler, java.util.logging.ConsoleHandler");

        props.setProperty("java.util.logging.FileHandler.pattern", _pattern);
        props.setProperty("java.util.logging.FileHandler.limit", String.valueOf(_limit));
        props.setProperty("java.util.logging.FileHandler.count", String.valueOf(_count));
        props.setProperty("java.util.logging.FileHandler.level", _level);
        props.setProperty("java.util.logging.FileHandler.append", "true");
        props.setProperty("java.util.logging.FileHandler.formatter", "xproxy.util.LogFormatter");

        props.setProperty("java.util.logging.ConsoleHandler.level", _level);
        props.setProperty("java.util.logging.ConsoleHandler.formatter", "xproxy.util.LogFormatter");

        props.setProperty("xproxy.level", _level);

        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos);
        props.store(pos, "");
        pos.close();
        LogManager.getLogManager().readConfiguration(pis);
        pis.close();
    }

}
