package com.queuectl.cli;

import com.queuectl.core.JobService;
import com.queuectl.core.Clock;
import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;
import java.time.Instant;

@CommandLine.Command(name="status", description="Show counts by state")
public class StatusCmd implements Runnable {
  @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
  public void run(){
    DataSource ds = DataSourceFactory.sqlite(db);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    JobService js = new JobService(ds, (Clock)Instant::now);
    var m = js.countsByState();
    System.out.printf("📊 Status: pending=%d processing=%d completed=%d failed=%d dead=%d%n",
      m.get("pending"), m.get("processing"), m.get("completed"), m.get("failed"), m.get("dead"));
  }
}
