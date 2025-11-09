package com.queuectl.cli;

import com.queuectl.core.JobService;
import com.queuectl.core.Clock;
import com.queuectl.db.DataSourceFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

@CommandLine.Command(
    name = "enqueue",
    description = "Add a new job to the queue."
)
public class EnqueueCmd implements Runnable {

  @CommandLine.Parameters(
      index = "0",
      paramLabel = "JSON",
      description = "Job JSON (e.g. {\"id\":\"job1\",\"queue\":\"default\",\"command\":\"sleep 1\"})"
  )
  private String json;

  @CommandLine.Option(names="--db", defaultValue="queuectl.db", description="SQLite DB path")
  private String db;

  @Override
  public void run() {
    DataSource ds = DataSourceFactory.sqlite(db);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

    Clock clock = Instant::now;
    JobService js = new JobService(ds, clock);

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = new ObjectMapper().readValue(json, Map.class);
      js.enqueue(map);
      System.out.printf("✅ Enqueued: id=%s queue=%s command=\"%s\"%n",
          map.get("id"), map.getOrDefault("queue","default"), map.get("command"));
    } catch (Exception e) {
      System.err.println("❌ enqueue error: " + e.getMessage());
      System.exit(1);
    }
  }
}
