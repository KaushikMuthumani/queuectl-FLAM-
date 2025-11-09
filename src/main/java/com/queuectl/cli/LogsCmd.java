package com.queuectl.cli;

import com.queuectl.core.JobService;
import com.queuectl.core.Clock;
import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;
import java.time.Instant;

@CommandLine.Command(name="logs", description="Show logs for a job")
public class LogsCmd implements Runnable {
  @CommandLine.Parameters(paramLabel="JOB_ID") String jobId;
  @CommandLine.Option(names="--limit", defaultValue="50") int limit;
  @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
  public void run(){
    DataSource ds = DataSourceFactory.sqlite(db);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    JobService js = new JobService(ds, (Clock)Instant::now);
    js.logs(jobId, limit).forEach(System.out::println);
  }
}
