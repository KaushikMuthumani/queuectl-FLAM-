package com.queuectl.cli;

import com.queuectl.core.JobService;
import com.queuectl.core.Clock;
import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;
import java.time.Instant;

@CommandLine.Command(name="dlq", description="DLQ operations", subcommands = {DlqCmd.List.class, DlqCmd.Retry.class})
public class DlqCmd implements Runnable {
  public void run(){ CommandLine.usage(this, System.out); }

  @CommandLine.Command(name="list", description="List DLQ")
  public static class List implements Runnable {
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      JobService js = new JobService(ds, (Clock)Instant::now);
      var rows = js.listDlq();
      System.out.println("☠️  DLQ:");
      rows.forEach(r -> System.out.printf("- id=%s queue=%s cmd=\"%s\" attempts=%d last_exit=%s%n",
        r.get("id"), r.get("queue"), r.get("command"), r.get("attempts"), r.get("last_exit_code")));
    }
  }

  @CommandLine.Command(name="retry", description="Retry job from DLQ by id")
  public static class Retry implements Runnable {
    @CommandLine.Parameters(paramLabel="ID") String id;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      JobService js = new JobService(ds, (Clock)Instant::now);
      js.retryFromDlq(id);
      System.out.println("🔁 Re-enqueued from DLQ: "+id);
    }
  }
}
