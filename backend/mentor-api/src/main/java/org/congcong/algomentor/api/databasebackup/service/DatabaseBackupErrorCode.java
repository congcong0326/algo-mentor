package org.congcong.algomentor.api.databasebackup.service;

/** Error codes intentionally exposed by the database backup administration API. */
public enum DatabaseBackupErrorCode {
  DATABASE_BACKUP_BUSY,
  DATABASE_BACKUP_FAILED,
  DATABASE_RESTORE_ARCHIVE_INVALID,
  DATABASE_RESTORE_CHECKSUM_MISMATCH,
  DATABASE_RESTORE_VERSION_MISMATCH,
  DATABASE_RESTORE_TABLE_SET_MISMATCH,
  DATABASE_RESTORE_PRIVILEGE_REQUIRED,
  DATABASE_RESTORE_FAILED
}
