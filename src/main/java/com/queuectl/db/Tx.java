package com.queuectl.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.function.Function;

public final class Tx {
  private final DataSource ds;
  public Tx(DataSource ds) { this.ds = ds; }
  public <T> T inTx(Function<Connection, T> fn) {
    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      try {
        T r = fn.apply(c);
        c.commit();
        return r;
      } catch (Exception e) {
        c.rollback();
        throw new RuntimeException(e);
      } finally {
        c.setAutoCommit(true);
      }
    } catch (Exception e) { throw new RuntimeException(e); }
  }
}
