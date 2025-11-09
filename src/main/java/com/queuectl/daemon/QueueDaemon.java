package com.queuectl.daemon;

import com.queuectl.core.*;
import com.queuectl.db.DataSourceFactory;
import com.queuectl.http.DashboardServer;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

public final class QueueDaemon {
  public static void start(String dbPath, Map<String,Integer> queueWorkers, boolean dashboard) {
    DataSource ds = DataSourceFactory.sqlite(dbPath);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

    Clock clock = Instant::now;
    JobService js = new JobService(ds, clock);
    QueueService qs = new QueueService(ds);

    int backoffBase = 2; // can be made dynamic from config
    Backoff backoff = new Backoff(backoffBase, 3600);

    // Sweeper
    LeaseSweeper sweeper = new LeaseSweeper(ds, clock);
    Thread sweeperThread = new Thread(() -> { while(true){ try{ sweeper.run(); Thread.sleep(5000);}catch(Exception ignored){} }}, "lease-sweeper");
    sweeperThread.setDaemon(true); sweeperThread.start();

    // Scheduler
    SchedulerService scheduler = new SchedulerService(js, clock);
    Thread schedThread = new Thread(scheduler, "scheduler");
    schedThread.setDaemon(true); schedThread.start();

    // Dashboard
    if (dashboard) {
      int port = 8088;
      DashboardServer.start(port, ds);
      System.out.println("Dashboard: http://localhost:"+port);
    }

    // Workers per queue
    List<Thread> threads = new ArrayList<>();
    List<WorkerService> workers = new ArrayList<>();
    for (var e: queueWorkers.entrySet()){
      String q = e.getKey();
      int n = e.getValue();
      for (int i=0;i<n;i++){
        var w = new WorkerService(q, js, backoff, qs.limiter(q));
        var t = new Thread(w, "w-"+q+"-"+i);
        threads.add(t); workers.add(w); t.start();
      }
    }

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      workers.forEach(WorkerService::requestStop);
      for (Thread t: threads) { try { t.join(5000);} catch (InterruptedException ignored){} }
      System.out.println("Workers stopped gracefully.");
    }));

    try { for (Thread t: threads) t.join(); } catch (InterruptedException ignored) {}
  }
}
