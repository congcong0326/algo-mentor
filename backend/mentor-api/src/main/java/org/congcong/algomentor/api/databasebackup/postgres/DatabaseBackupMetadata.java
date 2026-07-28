package org.congcong.algomentor.api.databasebackup.postgres;

import java.util.List;

/** Environment values that must match before imported data can replace the current database. */
public record DatabaseBackupMetadata(
    String applicationVersion,
    int postgresMajorVersion,
    String flywayFingerprint,
    List<String> tables
) {

  public DatabaseBackupMetadata {
    tables = List.copyOf(tables);
  }
}
