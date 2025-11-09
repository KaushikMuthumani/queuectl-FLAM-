package com.queuectl.core;

import com.queuectl.model.Job;

import java.util.Random;
import java.util.UUID;

public final class WorkerService implements Runnable {
  private final String queue;
  private final String workerId = "w-" + UUID.randomUUID();
  private final JobService jobService;
  private final Backoff backoff;
  private final CommandExecutor exec = new CommandExecutor();
  private final RateLimiter limiter;
  private volatile boolean stop = false;

  public WorkerService(String queue, JobService js, Backoff backoff, RateLimiter limiter) {
    this.queue=queue; this.jobService = js; this.backoff = backoff; this.limiter=limiter;
  }

  public void requestStop() { stop = true; }

  public void run() {
    Random jitter = new Random();
    while (!stop) {
      try {
        if (!limiter.tryAcquire()) { Thread.sleep(20); continue; }
        var claimed = jobService.claimNext(queue, workerId, 30);
        if (claimed.isEmpty()) { Thread.sleep(200 + jitter.nextInt(600)); continue; }
        Job j = claimed.get();
        var res = exec.run(j.command(), j.timeoutSec());
        if (res.timedOut()) {
          jobService.onFail(j, 124, "[timeout] " + res.stderr(), backoff);
        } else if (res.exitCode() == 0) {
          jobService.markCompleted(j.id(), 0, res.stdout());
        } else {
          jobService.onFail(j, res.exitCode(), res.stderr(), backoff);
        }
      } catch (Exception e) {
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
      }
    }
  }
}
