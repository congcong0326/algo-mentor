package org.congcong.algomentor.api.databasebackup.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Configuration for administrator-operated PostgreSQL data backups. */
@ConfigurationProperties(prefix = DatabaseBackupProperties.PREFIX)
public class DatabaseBackupProperties {

  public static final String PREFIX = "algo-mentor.database-backup";

  private boolean enabled;
  private String pgDumpPath = "pg_dump";
  private String pgRestorePath = "pg_restore";
  private String psqlPath = "psql";
  private Duration commandTimeout = Duration.ofMinutes(10);
  private DataSize maxUploadSize = DataSize.ofGigabytes(2);
  private String applicationVersion = "0.1.0-SNAPSHOT";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getPgDumpPath() {
    return pgDumpPath;
  }

  public void setPgDumpPath(String pgDumpPath) {
    this.pgDumpPath = pgDumpPath;
  }

  public String getPgRestorePath() {
    return pgRestorePath;
  }

  public void setPgRestorePath(String pgRestorePath) {
    this.pgRestorePath = pgRestorePath;
  }

  public String getPsqlPath() {
    return psqlPath;
  }

  public void setPsqlPath(String psqlPath) {
    this.psqlPath = psqlPath;
  }

  public Duration getCommandTimeout() {
    return commandTimeout;
  }

  public void setCommandTimeout(Duration commandTimeout) {
    this.commandTimeout = commandTimeout;
  }

  public DataSize getMaxUploadSize() {
    return maxUploadSize;
  }

  public void setMaxUploadSize(DataSize maxUploadSize) {
    this.maxUploadSize = maxUploadSize;
  }

  public String getApplicationVersion() {
    return applicationVersion;
  }

  public void setApplicationVersion(String applicationVersion) {
    this.applicationVersion = applicationVersion;
  }
}
