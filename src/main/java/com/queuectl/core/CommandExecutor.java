package com.queuectl.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class CommandExecutor {
  public record Result(int exitCode, String stdout, String stderr, boolean timedOut) {}

  public Result run(String cmd, int timeoutSec) throws Exception {
    ProcessBuilder pb = new ProcessBuilder("/bin/sh","-c", cmd);
    Process p = pb.start();
    StreamCollector out = new StreamCollector(p.getInputStream());
    StreamCollector err = new StreamCollector(p.getErrorStream());
    out.start(); err.start();
    boolean done = p.waitFor(timeoutSec, TimeUnit.SECONDS);
    if(!done){
      p.destroyForcibly(); out.join(); err.join();
      return new Result(124, out.getCollected(2048), err.getCollected(2048), true);
    }
    int code = p.exitValue(); out.join(); err.join();
    return new Result(code, out.getCollected(4096), err.getCollected(4096), false);
  }

  static final class StreamCollector extends Thread {
    private final BufferedReader br; private final StringBuilder sb=new StringBuilder();
    StreamCollector(java.io.InputStream is){ this.br = new BufferedReader(new InputStreamReader(is)); }
    public void run(){ try{ String line; while((line=br.readLine())!=null){ sb.append(line).append('\n'); } } catch(Exception ignored){} }
    String getCollected(int max){ return sb.length()>max? sb.substring(sb.length()-max):sb.toString(); }
  }
}
