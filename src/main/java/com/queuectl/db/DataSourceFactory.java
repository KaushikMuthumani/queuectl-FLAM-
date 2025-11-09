package com.queuectl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

public final class DataSourceFactory {
  public static DataSource sqlite(String path) {
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl("jdbc:sqlite:" + path);
    cfg.setMaximumPoolSize(8);
    cfg.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");
    return new HikariDataSource(cfg);
  }
}
