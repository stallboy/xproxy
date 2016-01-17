package xproxy.io;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class NetModel {
    static final Object sync = new Object();
    static FixedCpuPoll pollPolicy;
    static ScheduledThreadPoolExecutor delayPool = new ScheduledThreadPoolExecutor(1);

    public static void initialize(int _cpu) throws IOException {
        synchronized (sync) {
            pollPolicy = new FixedCpuPoll(_cpu);
        }
    }

    public static void shutdown() {
        synchronized (sync) {
            if (pollPolicy != null)
                pollPolicy.shutdown();
        }
    }

    public static PollTask register(SelectableChannel channel, int ops, Handler io) throws IOException {
        if (pollPolicy == null) {
            synchronized (sync) {
                if (pollPolicy == null)
                    pollPolicy = new FixedCpuPoll(1);
            }
        }

        return pollPolicy.register(channel, ops, io);
    }

    public static ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return delayPool.schedule(command, delay, unit);
    }

}
