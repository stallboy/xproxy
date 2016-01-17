package xproxy.io;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FixedCpuPoll {
	private final Queue<PollTask> q;
	private final ExecutorService p;

	FixedCpuPoll(int ncpu) throws IOException {
		q = new PriorityQueue<>();
		p = Executors.newFixedThreadPool(ncpu, r -> {
            Thread thr = new Thread(r);
            thr.setName("PollService-" + thr.getId());
            return thr;
        });

		for (int i = 0; i < ncpu; i++) {
			PollTask task = new PollTask();
			q.add(task);
			p.execute(task);
		}
	}

	void schedule(PollTask task) {
		p.execute(task);
	}

	PollTask register(SelectableChannel channel, int ops, Handler io) throws IOException {
		synchronized (q) {
			PollTask poll = q.poll();
			try {
				poll.register(channel, ops, io);
				return poll;
			} finally {
				q.offer(poll);
			}
		}
	}

	void shutdown() {
		p.shutdown();
		try {
			if (!p.awaitTermination(1, TimeUnit.SECONDS)) {
				p.shutdownNow();
				p.awaitTermination(1, TimeUnit.SECONDS);
			}
		} catch (InterruptedException e) {
			p.shutdownNow();
			Thread.currentThread().interrupt();
		} finally {
			for (PollTask task; (task = q.poll()) != null;)
				task.close();
		}
	}

	

}