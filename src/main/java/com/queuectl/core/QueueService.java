package com.queuectl.core;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class QueueService {
  private final DataSource ds;
  private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();
  public QueueService(DataSource ds){ this.ds=ds; preload(); }
  private void preload(){
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT name, rate_limit_per_sec FROM queues")) {
      try (ResultSet rs = ps.executeQuery()){ while(rs.next()){ limiters.put(rs.getString(1), new RateLimiter(rs.getInt(2))); } }
    } catch (Exception ignored){}
  }
  public RateLimiter limiter(String queue){
    return limiters.computeIfAbsent(queue, q -> new RateLimiter(50));
  }
}
