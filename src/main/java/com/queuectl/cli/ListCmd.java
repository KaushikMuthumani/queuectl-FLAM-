package com.queuectl.cli;

import com.queuectl.core.JobService;
import com.queuectl.core.Clock;
import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;
import java.time.Instant;

@CommandLine.Command(name="list", description="List jobs by state")
public class ListCmd implements Runnable {
  @CommandLine.Option(names="--state", required = true) String state;
  @CommandLine.Option(names="--limit", defaultValue="20") int limit;
  @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
  public void run(){
    DataSource ds = DataSourceFactory.sqlite(db);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    JobService js = new JobService(ds, (Clock)Instant::now);
    var list = js.list(state, limit);
    System.out.printf("🧾 Jobs (state=%s, limit=%d)%n", state, limit);
    for (var j: list) {
      System.out.printf("- id=%s attempts=%d prio=%d cmd=\"%s\" run_after=%s%n",
        j.id(), j.attempts(), j.priority(), j.command(), j.runAfter());
    }
  }
}
