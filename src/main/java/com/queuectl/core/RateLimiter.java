package com.queuectl.core;

public final class RateLimiter {
  private final int capacity; private final double refillPerSec; private double tokens; private long lastNanos;
  public RateLimiter(int ratePerSec){ this.capacity=Math.max(1,ratePerSec); this.refillPerSec=ratePerSec; this.tokens=capacity; this.lastNanos=System.nanoTime(); }
  public synchronized boolean tryAcquire(){
    long now=System.nanoTime();
    double add=((now-lastNanos)/1_000_000_000.0)*refillPerSec;
    tokens=Math.min(capacity, tokens+add); lastNanos=now;
    if(tokens>=1.0){ tokens-=1.0; return true; }
    return false;
  }
}
