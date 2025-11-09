package com.queuectl.core;
public final class Idempotency {
  public static String cronKey(String jobId, long epochMinute){ return "cron:"+jobId+":"+epochMinute; }
}
