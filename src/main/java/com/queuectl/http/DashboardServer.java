package com.queuectl.http;

import spark.Spark;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public final class DashboardServer {
  public static void start(int port, DataSource ds) {
    Spark.port(port);

    // Landing page to avoid 404
    Spark.get("/", (req, res) -> {
      res.type("text/html");
      return """
      <html><head><title>QueueCTL</title></head>
      <body style="font-family: monospace">
        <h2>QueueCTL Dashboard</h2>
        <ul>
          <li><a href="/status">/status</a></li>
          <li><a href="/jobs">/jobs</a></li>
          <li><a href="/jobs?state=pending">/jobs?state=pending</a></li>
          <li><a href="/health">/health</a></li>
        </ul>
      </body></html>
      """;
    });

    Spark.get("/health", (req, res) -> "ok");

    Spark.get("/status", (req, res) -> {
      res.type("application/json");
      Map<String,Object> m = new LinkedHashMap<>();
      try (Connection c = ds.getConnection()) {
        m.put("pending", count(c, "pending"));
        m.put("processing", count(c, "processing"));
        m.put("completed", count(c, "completed"));
        m.put("failed", count(c, "failed"));
        m.put("dead", count(c, "dead"));
      }
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
    });

    Spark.get("/jobs", (req, res) -> {
      String state = req.queryParams("state");
      res.type("application/json");
      try (Connection c = ds.getConnection();
           PreparedStatement ps = state==null
             ? c.prepareStatement("SELECT id, state, command, attempts, priority, run_after FROM jobs ORDER BY created_at DESC LIMIT 100")
             : c.prepareStatement("SELECT id, state, command, attempts, priority, run_after FROM jobs WHERE state=? ORDER BY created_at DESC LIMIT 100")) {
        if (state!=null) ps.setString(1, state);
        try (ResultSet rs = ps.executeQuery()) {
          List<Map<String,Object>> list = new ArrayList<>();
          while (rs.next()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("state", rs.getString("state"));
            row.put("command", rs.getString("command"));
            row.put("attempts", rs.getInt("attempts"));
            row.put("priority", rs.getInt("priority"));
            row.put("run_after", rs.getString("run_after"));
            list.add(row);
          }
          return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(list);
        }
      }
    });
  }

  private static long count(Connection c, String s) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM jobs WHERE state=?")) {
      ps.setString(1, s);
      try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
    }
  }
}
