package org.congcong.algomentor.api.databasebackup.postgres;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;

/** Converts the configured JDBC endpoint into a libpq endpoint without embedding credentials. */
public record PostgresConnectionDetails(String connectionUri, String username, String password) {

  public static PostgresConnectionDetails fromJdbcUrl(String jdbcUrl, String username, String password) {
    if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")) {
      throw invalidConfiguration("Database backup requires a PostgreSQL JDBC URL.");
    }
    try {
      URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
      if (!"postgresql".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
          || uri.getRawUserInfo() != null || includesCredentialQuery(uri.getRawQuery())) {
        throw invalidConfiguration("Database backup URL must not contain database credentials.");
      }
      URI libpqUri = new URI(
          "postgresql",
          null,
          uri.getHost(),
          uri.getPort(),
          uri.getRawPath(),
          uri.getRawQuery(),
          null);
      return new PostgresConnectionDetails(
          libpqUri.toASCIIString(),
          username == null ? "" : username,
          password == null ? "" : password);
    } catch (URISyntaxException exception) {
      throw invalidConfiguration("Database backup requires a valid PostgreSQL JDBC URL.");
    }
  }

  public java.util.Map<String, String> processEnvironment() {
    if (username.isBlank()) {
      throw invalidConfiguration("Database backup requires spring.datasource.username.");
    }
    java.util.Map<String, String> environment = new java.util.HashMap<>();
    environment.put("PGUSER", username);
    if (!password.isBlank()) {
      environment.put("PGPASSWORD", password);
    }
    return environment;
  }

  private static boolean includesCredentialQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isBlank()) {
      return false;
    }
    for (String part : rawQuery.split("&")) {
      int separator = part.indexOf('=');
      String key = separator < 0 ? part : part.substring(0, separator);
      String decoded = URLDecoder.decode(key, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
      if (decoded.equals("user") || decoded.equals("password")) {
        return true;
      }
    }
    return false;
  }

  private static DatabaseBackupException invalidConfiguration(String message) {
    return new DatabaseBackupException(DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED, message);
  }
}
