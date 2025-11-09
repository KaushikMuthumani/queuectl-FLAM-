package com.queuectl.model;

import java.time.Instant;

public record Job(
  String id,
  String queue,
  String command,
  String args,
  JobState state,
  int attempts,
  int maxRetries,
  int priority,
  int timeoutSec,
  String idempotencyKey,
  Instant createdAt,
  Instant updatedAt,
  Instant runAfter,
  Instant leaseUntil,
  String workerId,
  Integer lastExitCode,
  String lastError
) {}
