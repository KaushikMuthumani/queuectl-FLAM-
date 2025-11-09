package com.queuectl.core;

import com.queuectl.model.Job;
import com.queuectl.model.JobState;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JobService {
  private final DataSource ds;
  private final Clock clock;
  public JobService(DataSource ds, Clock clock){ this.ds=ds; this.clock=clock; }

  public void enqueue(Map<String,Object> json){
    String id=(String)json.get("id");
    String queue=(String)json.getOrDefault("queue","default");
    String command=(String)json.get("command");
    String args=(String)json.getOrDefault("args", null);
    String idk=(String)json.getOrDefault("idempotency_key", null);
    int maxRetries = toInt(json.getOrDefault("max_retries",3));
    int priority = toInt(json.getOrDefault("priority",0));
    int timeoutSec = toInt(json.getOrDefault("timeout_sec",60));
    Instant now = clock.now();
    Instant runAfter = json.containsKey("run_after") ? Instant.parse((String)json.get("run_after")) : now;
    String cron = (String)json.getOrDefault("cron", null);

    if(id==null || command==null) throw new IllegalArgumentException("id and command required");

    try(Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
      "INSERT INTO jobs(id,queue,command,args,state,attempts,max_retries,priority,timeout_sec,idempotency_key,run_after,cron,created_at,updated_at) VALUES(?, ?, ?, ?, 'pending', 0, ?, ?, ?, ?, ?, ?, ?, ?)"
    )){
      ps.setString(1,id); ps.setString(2,queue); ps.setString(3,command); ps.setString(4,args);
      ps.setInt(5,maxRetries); ps.setInt(6,priority); ps.setInt(7,timeoutSec); ps.setString(8,idk);
      ps.setString(9,runAfter.toString()); ps.setString(10,cron);
      ps.setString(11,now.toString()); ps.setString(12,now.toString());
      ps.executeUpdate();
    } catch(SQLException e){
      if(e.getMessage()!=null && e.getMessage().contains("PRIMARY KEY")) throw new IllegalStateException("Job id exists: "+id);
      if(e.getMessage()!=null && e.getMessage().contains("ux_jobs_idem")) throw new IllegalStateException("Idempotent duplicate for key: "+idk);
      throw new RuntimeException(e);
    }
  }

  private int toInt(Object o){ return o instanceof Number n? n.intValue(): Integer.parseInt(String.valueOf(o)); }

  public Optional<Job> claimNext(String queue, String workerId, int leaseSeconds){
    Instant now = clock.now();
    Instant leaseUntil = now.plusSeconds(leaseSeconds);
    try (Connection c = ds.getConnection()) {
      c.setAutoCommit(false);
      String select = """
        SELECT j.id FROM jobs j
        WHERE j.state='pending' AND j.queue=? AND j.run_after <= ?
          AND NOT EXISTS (SELECT 1 FROM job_deps d JOIN jobs p ON d.depends_on=p.id WHERE d.job_id=j.id AND p.state!='completed')
        ORDER BY j.priority DESC, j.created_at ASC
        LIMIT 1
      """;
      String update = """
        UPDATE jobs SET state='processing', worker_id=?, lease_until=?, updated_at=?
        WHERE id=? AND state='pending' AND run_after <= ?
      """;
      String id = null;
      try (PreparedStatement s=c.prepareStatement(select)){
        s.setString(1, queue); s.setString(2, now.toString());
        try(ResultSet rs = s.executeQuery()){ if(rs.next()) id = rs.getString(1); }
      }
      if (id == null){ c.rollback(); c.setAutoCommit(true); return Optional.empty(); }
      try (PreparedStatement u=c.prepareStatement(update)){
        u.setString(1, workerId); u.setString(2, leaseUntil.toString()); u.setString(3, now.toString());
        u.setString(4, id); u.setString(5, now.toString());
        int rows = u.executeUpdate();
        if (rows==0){ c.rollback(); c.setAutoCommit(true); return Optional.empty(); }
      }
      Job job = getByIdConn(c, id).orElseThrow();
      c.commit(); c.setAutoCommit(true);
      return Optional.of(job);
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public void markCompleted(String id, int exitCode, String stdoutTail){
    Instant now = clock.now();
    try (Connection c = ds.getConnection()){
      c.setAutoCommit(false);
      try (PreparedStatement ps=c.prepareStatement("UPDATE jobs SET state='completed', updated_at=?, last_exit_code=? WHERE id=? AND state='processing'")){
        ps.setString(1, now.toString()); ps.setInt(2, exitCode); ps.setString(3, id); ps.executeUpdate();
      }
      if (stdoutTail!=null && !stdoutTail.isEmpty()) logLineConn(c, id, "stdout", stdoutTail);
      c.commit(); c.setAutoCommit(true);
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public void onFail(Job job, int exitCode, String error, Backoff backoff){
    Instant now = clock.now();
    int next = job.attempts()+1;
    if (next > job.maxRetries()){
      moveToDlq(job.id(), exitCode, error);
      return;
    }
    int delay = backoff.delaySeconds(next);
    Instant nextRun = now.plusSeconds(delay);
    try (Connection c = ds.getConnection()){
      c.setAutoCommit(false);
      try (PreparedStatement ps=c.prepareStatement("""
        UPDATE jobs SET state='pending', attempts=?, updated_at=?, run_after=?, last_exit_code=?, last_error=NULL, worker_id=NULL, lease_until=NULL
        WHERE id=?""")){
        ps.setInt(1,next); ps.setString(2, now.toString()); ps.setString(3, nextRun.toString());
        ps.setInt(4, exitCode); ps.setString(5, job.id()); ps.executeUpdate();
      }
      logLineConn(c, job.id(), "stderr", truncate(error, 1024));
      c.commit(); c.setAutoCommit(true);
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  private void moveToDlq(String id, int exitCode, String error){
    Instant now = clock.now();
    try (Connection c = ds.getConnection()){
      c.setAutoCommit(false);
      Job j = getByIdConn(c, id).orElseThrow();
      try (PreparedStatement ins=c.prepareStatement("""
        INSERT INTO dlq(id, queue, command, attempts, last_exit_code, last_error, moved_at) VALUES(?,?,?,?,?,?,?)""")){
        ins.setString(1, j.id()); ins.setString(2, j.queue()); ins.setString(3, j.command());
        ins.setInt(4, j.attempts()+1); ins.setInt(5, exitCode); ins.setString(6, truncate(error, 2048));
        ins.setString(7, now.toString()); ins.executeUpdate();
      }
      try (PreparedStatement upd=c.prepareStatement("UPDATE jobs SET state='dead', updated_at=? WHERE id=?")){
        upd.setString(1, now.toString()); upd.setString(2, id); upd.executeUpdate();
      }
      c.commit(); c.setAutoCommit(true);
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public void retryFromDlq(String id){
    try (Connection c = ds.getConnection()){
      c.setAutoCommit(false);
      String sel="SELECT id, queue, command FROM dlq WHERE id=?";
      try (PreparedStatement s=c.prepareStatement(sel)){ s.setString(1,id); try (ResultSet rs=s.executeQuery()){
        if(!rs.next()) throw new IllegalArgumentException("DLQ not found: "+id);
        String jid=rs.getString(1), queue=rs.getString(2), cmd=rs.getString(3);
        Map<String,Object> m = new HashMap<>(); m.put("id", jid); m.put("queue", queue); m.put("command", cmd);
        enqueue(m);
      }}
      try (PreparedStatement d=c.prepareStatement("DELETE FROM dlq WHERE id=?")){ d.setString(1, id); d.executeUpdate(); }
      c.commit(); c.setAutoCommit(true);
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public void materializeCron(Instant now){
    long minute = now.getEpochSecond()/60;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement("SELECT id, queue, command FROM jobs WHERE cron IS NOT NULL")) {
      try (ResultSet rs = ps.executeQuery()){
        while(rs.next()){
          String baseId = rs.getString(1), q=rs.getString(2), cmd=rs.getString(3);
          String idem = Idempotency.cronKey(baseId, minute);
          Map<String,Object> m = new HashMap<>();
          m.put("id", baseId+"-i-"+minute); m.put("queue", q); m.put("command", cmd); m.put("idempotency_key", idem);
          try { enqueue(m); } catch (Exception ignored){}
        }
      }
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public Map<String,Long> countsByState(){
    Map<String,Long> m = new LinkedHashMap<>();
    String[] states = {"pending","processing","completed","failed","dead"};
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM jobs WHERE state=?")){
      for (String s: states){ ps.setString(1, s); try (ResultSet rs = ps.executeQuery()){ rs.next(); m.put(s, rs.getLong(1)); } }
    } catch (SQLException e){ throw new RuntimeException(e); }
    return m;
  }

  public List<Job> list(String state, int limit){
    String sql = "SELECT * FROM jobs WHERE state=? ORDER BY created_at DESC LIMIT ?";
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)){
      ps.setString(1, state); ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()){
        List<Job> out = new ArrayList<>(); while(rs.next()) out.add(map(rs)); return out;
      }
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public List<Map<String,Object>> listDlq(){
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT id, queue, command, attempts, last_exit_code FROM dlq ORDER BY moved_at DESC")){
      try(ResultSet rs = ps.executeQuery()){
        List<Map<String,Object>> out = new ArrayList<>();
        while(rs.next()){
          Map<String,Object> m = new LinkedHashMap<>();
          m.put("id", rs.getString(1)); m.put("queue", rs.getString(2)); m.put("command", rs.getString(3));
          m.put("attempts", rs.getInt(4)); m.put("last_exit_code", rs.getInt(5)); out.add(m);
        }
        return out;
      }
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  public List<String> logs(String jobId, int limit){
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT created_at || ' ' || kind || ' ' || content FROM job_logs WHERE job_id=? ORDER BY id DESC LIMIT ?")){
      ps.setString(1, jobId); ps.setInt(2, limit);
      try (ResultSet rs = ps.executeQuery()){ List<String> out=new ArrayList<>(); while(rs.next()) out.add(rs.getString(1)); return out; }
    } catch (SQLException e){ throw new RuntimeException(e); }
  }

  // ----- helpers -----
  private Optional<Job> getByIdConn(Connection c, String id) throws SQLException {
    try (PreparedStatement ps=c.prepareStatement("SELECT * FROM jobs WHERE id=?")){ ps.setString(1, id); try (ResultSet rs=ps.executeQuery()){ return rs.next()? Optional.of(map(rs)): Optional.empty(); } }
  }
  private static Job map(ResultSet r) throws SQLException {
    return new Job(
      r.getString("id"), r.getString("queue"), r.getString("command"), r.getString("args"),
      JobState.valueOf(r.getString("state")), r.getInt("attempts"), r.getInt("max_retries"),
      r.getInt("priority"), r.getInt("timeout_sec"), r.getString("idempotency_key"),
      Instant.parse(r.getString("created_at")), Instant.parse(r.getString("updated_at")),
      Instant.parse(r.getString("run_after")),
      r.getString("lease_until")==null? null: Instant.parse(r.getString("lease_until")),
      r.getString("worker_id"), (Integer)r.getObject("last_exit_code"), r.getString("last_error")
    );
  }
  private void logLineConn(Connection c, String jobId, String kind, String content) throws SQLException {
    try (PreparedStatement ps=c.prepareStatement("INSERT INTO job_logs(job_id,created_at,kind,content) VALUES(?,?,?,?)")){
      ps.setString(1, jobId); ps.setString(2, clock.now().toString()); ps.setString(3, kind); ps.setString(4, content); ps.executeUpdate();
    }
  }
  private static String truncate(String s, int n){ if(s==null) return null; return s.length()>n? s.substring(0,n):s; }
}
