package com.queuectl.core;

import java.util.concurrent.ThreadLocalRandom;

public final class Backoff {
  private final int base;
  private final int capSeconds;
  public Backoff(int base, int capSeconds) { this.base = Math.max(2, base); this.capSeconds = Math.max(10, capSeconds); }
  public int delaySeconds(int attempts) {
    long d=1; for(int i=0;i<attempts;i++) d*=base;
    d = Math.min(d, capSeconds);
    int jitter = ThreadLocalRandom.current().nextInt(0, base);
    return (int)Math.min(Integer.MAX_VALUE, d + jitter);
  }
}
