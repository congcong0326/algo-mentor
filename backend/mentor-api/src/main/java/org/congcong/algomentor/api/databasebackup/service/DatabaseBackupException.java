package org.congcong.algomentor.api.databasebackup.service;

public class DatabaseBackupException extends RuntimeException {

  private final DatabaseBackupErrorCode code;

  public DatabaseBackupException(DatabaseBackupErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public DatabaseBackupException(DatabaseBackupErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public DatabaseBackupErrorCode code() {
    return code;
  }
}
