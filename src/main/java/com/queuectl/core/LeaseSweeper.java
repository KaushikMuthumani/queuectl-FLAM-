package com.queuectl.core;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

public final class LeaseSweeper implements Runnable {
  private final DataSource ds; private final Clock clock;
  public LeaseSweeper(DataSource ds, Clock clock){ this.ds=ds; this.clock=clock; }
  public void run(){
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement("""
           UPDATE jobs
             SET state='pending', worker_id=NULL, lease_until=NULL, updated_at=?
           WHERE state='processing' AND lease_until IS NOT NULL AND lease_until < ?
         """)) {
      var now = clock.now().toString();
      ps.setString(1, now); ps.setString(2, now); ps.executeUpdate();
    } catch(Exception ignored){}
  }
}
