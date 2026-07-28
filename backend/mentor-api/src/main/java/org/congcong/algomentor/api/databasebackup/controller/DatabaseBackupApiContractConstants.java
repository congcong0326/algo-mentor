package org.congcong.algomentor.api.databasebackup.controller;

/** HTTP and multipart contract constants for administrator database backup operations. */
public final class DatabaseBackupApiContractConstants {

  public static final String ADMIN_DATABASE_BASE_PATH = "/api/admin/database";
  public static final String BACKUP_PATH = "/backup";
  public static final String RESTORE_PATH = "/restore";
  public static final String FILE_PART = "file";
  public static final String CONFIRMATION_PART = "confirmation";
  public static final String ARCHIVE_MEDIA_TYPE = "application/octet-stream";
  public static final String MULTIPART_MEDIA_TYPE = "multipart/form-data";
  public static final String CACHE_CONTROL_NO_STORE = "no-store";

  private DatabaseBackupApiContractConstants() {
  }
}
