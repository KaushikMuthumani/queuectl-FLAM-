package com.queuectl.cli;

import com.queuectl.db.DataSourceFactory;
import org.flywaydb.core.Flyway;
import picocli.CommandLine;
import javax.sql.DataSource;

@CommandLine.Command(name="config", description="Get/Set config", subcommands = {ConfigCmd.Get.class, ConfigCmd.Set.class})
public class ConfigCmd implements Runnable {
  public void run(){ CommandLine.usage(this, System.out); }

  @CommandLine.Command(name="get")
  public static class Get implements Runnable {
    @CommandLine.Parameters(paramLabel="KEY") String key;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      try (var c=ds.getConnection(); var ps=c.prepareStatement("SELECT value FROM config WHERE key=?")){
        ps.setString(1, key); try (var rs=ps.executeQuery()){
          System.out.println(rs.next()? rs.getString(1): "(not set)");
        }
      } catch(Exception e){ System.err.println(e.getMessage()); System.exit(1); }
    }
  }

  @CommandLine.Command(name="set")
  public static class Set implements Runnable {
    @CommandLine.Parameters(index="0", paramLabel="KEY") String key;
    @CommandLine.Parameters(index="1", paramLabel="VALUE") String value;
    @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
    public void run(){
      DataSource ds = DataSourceFactory.sqlite(db);
      Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
      try (var c=ds.getConnection(); var ps=c.prepareStatement("INSERT INTO config(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value")){
        ps.setString(1, key); ps.setString(2, value); ps.executeUpdate(); System.out.println("ok");
      } catch(Exception e){ System.err.println(e.getMessage()); System.exit(1); }
    }
  }
}
