package org.congcong.algomentor.api.databasebackup.model;

import java.time.Instant;
import java.util.List;

/** Stable contents of the manifest.json entry in an .ambak archive. */
public record DatabaseBackupManifest(
    int formatVersion,
    String application,
    String applicationVersion,
    Instant createdAt,
    int postgresMajorVersion,
    String schema,
    String flywayFingerprint,
    List<String> tables,
    int tableCount,
    long dumpSizeBytes,
    String dumpSha256
) {

  public DatabaseBackupManifest {
    tables = tables == null ? List.of() : List.copyOf(tables);
  }
}
