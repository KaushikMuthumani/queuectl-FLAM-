package com.queuectl.cli;

import com.queuectl.daemon.QueueDaemon;
import picocli.CommandLine;
import java.util.*;

@CommandLine.Command(name="worker", description="Start workers/daemon")
public class WorkerCmd implements Runnable {
  @CommandLine.Option(names="--start", description="Start daemon", required = false) boolean start;
  @CommandLine.Option(names="--queues", description="queue:count[,queue2:count2]", defaultValue="default:2") String queuesArg;
  @CommandLine.Option(names="--db", defaultValue="queuectl.db") String db;
  @CommandLine.Option(names="--dashboard", defaultValue="false") boolean dashboard;

  public void run(){
    if (!start) {
      System.out.println("Usage: queuectl worker --start --queues default:2 --dashboard");
      return;
    }
    Map<String,Integer> q = new LinkedHashMap<>();
    for (String part: queuesArg.split(",")){
      String[] kv = part.split(":");
      q.put(kv[0], Integer.parseInt(kv[1]));
    }
    QueueDaemon.start(db, q, dashboard);
  }
}
