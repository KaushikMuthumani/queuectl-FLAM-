package com.queuectl;

import com.queuectl.cli.*;
import picocli.CommandLine;

@CommandLine.Command(
  name = "queuectl",
  mixinStandardHelpOptions = true,
  subcommands = {
    EnqueueCmd.class, WorkerCmd.class, StatusCmd.class, ListCmd.class,
    DlqCmd.class, ConfigCmd.class, LogsCmd.class, QueueCmd.class
  },
  description = "QueueCTL - production-style background job queue"
)
public class QueueCtl implements Runnable {
  public void run() { CommandLine.usage(this, System.out); }
  public static void main(String[] args) {
    System.exit(new CommandLine(new QueueCtl()).execute(args));
  }
}
