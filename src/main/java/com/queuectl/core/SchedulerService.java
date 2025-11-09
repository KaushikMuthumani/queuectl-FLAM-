package com.queuectl.core;

public final class SchedulerService implements Runnable {
  private final JobService jobs; private final Clock clock;
  public SchedulerService(JobService js, Clock clock){ this.jobs=js; this.clock=clock; }
  public void run() {
    while (true) {
      try {
        jobs.materializeCron(clock.now());
        Thread.sleep(1000);
      } catch (Exception e) {
        try{ Thread.sleep(500);}catch(Exception ignored){}
      }
    }
  }
}
