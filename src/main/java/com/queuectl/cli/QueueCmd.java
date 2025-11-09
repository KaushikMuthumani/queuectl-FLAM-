package com.queuectl.cli;

import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;

@CommandLine.Command(name="queue", description="Manage queues", subcommands={QueueCmd.Create.class, QueueCmd.Pause.class, QueueCmd.Resume.class})
public class QueueCmd implements Runnable {
  public void run(){ CommandLine.usage(this, System.out); }

  @CommandLine.Command(name="create")
  public static class Create implements Runnable {
    @CommandLine.Parameters(paramLabel="NAME") String name;
    @CommandLine.Option(names="--rate", defaultValue="50") int rate;
    @CommandLine.Option(names="--concurrency", defaultValue="2") int conc;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      try (var c=ds.getConnection(); var ps=c.prepareStatement("INSERT INTO queues(name, rate_limit_per_sec, concurrency, paused) VALUES(?,?,?,0) ON CONFLICT(name) DO UPDATE SET rate_limit_per_sec=excluded.rate_limit_per_sec, concurrency=excluded.concurrency")){
        ps.setString(1, name); ps.setInt(2, rate); ps.setInt(3, conc); ps.executeUpdate(); System.out.println("ok");
      } catch(Exception e){ System.err.println(e.getMessage()); System.exit(1); }
    }
  }

  @CommandLine.Command(name="pause")
  public static class Pause implements Runnable {
    @CommandLine.Parameters(paramLabel="NAME") String name;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      try (var c=ds.getConnection(); var ps=c.prepareStatement("UPDATE queues SET paused=1 WHERE name=?")){
        ps.setString(1, name); ps.executeUpdate(); System.out.println("paused");
      } catch(Exception e){ System.err.println(e.getMessage()); System.exit(1); }
    }
  }

  @CommandLine.Command(name="resume")
  public static class Resume implements Runnable {
    @CommandLine.Parameters(paramLabel="NAME") String name;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      try (var c=ds.getConnection(); var ps=c.prepareStatement("UPDATE queues SET paused=0 WHERE name=?")){
        ps.setString(1, name); ps.executeUpdate(); System.out.println("resumed");
      } catch(Exception e){ System.err.println(e.getMessage()); System.exit(1); }
    }
  }
}
