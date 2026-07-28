package org.congcong.algomentor.api.databasebackup.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;

/** Minimal operation-level metrics without recording sensitive backup metadata. */
public final class DatabaseBackupMetrics {

  private static final String OPERATION_DURATION = "algo_mentor_database_backup_operation_duration";
  private final MeterRegistry meterRegistry;

  public DatabaseBackupMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void record(String operation, String outcome, long startedAtNanos) {
    if (meterRegistry == null) {
      return;
    }
    Timer.builder(OPERATION_DURATION)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .register(meterRegistry)
        .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
  }
}
