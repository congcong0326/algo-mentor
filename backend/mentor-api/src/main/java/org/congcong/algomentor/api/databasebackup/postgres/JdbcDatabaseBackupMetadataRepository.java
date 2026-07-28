package org.congcong.algomentor.api.databasebackup.postgres;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;

/** Reads PostgreSQL catalog and Flyway values used by backup compatibility checks. */
public class JdbcDatabaseBackupMetadataRepository {

  private static final String TABLES_SQL = """
      SELECT c.relname
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
      ORDER BY c.relname
      """;
  private static final String FLYWAY_SQL = """
      SELECT COALESCE(version, ''), script, COALESCE(checksum::text, '')
      FROM flyway_schema_history
      WHERE success = true
      ORDER BY installed_rank
      """;
  private static final String TABLE_PRIVILEGES_SQL = """
      SELECT
        COALESCE(
          bool_and(has_table_privilege(c.oid, 'TRUNCATE, INSERT'))
            FILTER (WHERE c.relkind IN ('r', 'p')),
          false),
        COALESCE(
          bool_and(has_sequence_privilege(c.oid, 'USAGE, UPDATE'))
            FILTER (WHERE c.relkind = 'S'),
          true)
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'S')
      """;

  private final DataSource dataSource;
  private final String applicationVersion;

  public JdbcDatabaseBackupMetadataRepository(DataSource dataSource, String applicationVersion) {
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    this.applicationVersion = requireText(applicationVersion, "applicationVersion");
  }

  public DatabaseBackupMetadata readCurrent() {
    try (Connection connection = dataSource.getConnection()) {
      int majorVersion = connection.getMetaData().getDatabaseMajorVersion();
      if (majorVersion < 1) {
        throw new SQLException("PostgreSQL major version is unavailable");
      }
      return new DatabaseBackupMetadata(
          applicationVersion,
          majorVersion,
          readFlywayFingerprint(connection),
          readTables(connection));
    } catch (SQLException exception) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Unable to inspect the current database for backup compatibility.",
          exception);
    }
  }

  /** Verifies all permissions needed before any restore command can truncate data. */
  public void verifyRestorePrivileges() {
    try (Connection connection = dataSource.getConnection()) {
      verifyDataPrivileges(connection);
      verifyReplicationRolePrivilege(connection);
    } catch (SQLException exception) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_PRIVILEGE_REQUIRED,
          "Database account lacks permissions required for a full data restore.",
          exception);
    }
  }

  private List<String> readTables(Connection connection) throws SQLException {
    List<String> tables = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(TABLES_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        tables.add(resultSet.getString(1));
      }
    }
    if (tables.isEmpty()) {
      throw new SQLException("public schema contains no tables");
    }
    return tables;
  }

  private String readFlywayFingerprint(Connection connection) throws SQLException {
    MessageDigest digest = sha256();
    try (PreparedStatement statement = connection.prepareStatement(FLYWAY_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        digest.update(resultSet.getString(1).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\t');
        digest.update(resultSet.getString(2).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\t');
        digest.update(resultSet.getString(3).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
      }
    }
    return "sha256:" + HexFormat.of().formatHex(digest.digest());
  }

  private static void verifyDataPrivileges(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(TABLE_PRIVILEGES_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next() || !resultSet.getBoolean(1) || !resultSet.getBoolean(2)) {
        throw new SQLException("database account lacks table or sequence restore privileges");
      }
    }
  }

  private static void verifyReplicationRolePrivilege(Connection connection) throws SQLException {
    boolean previousAutoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try (Statement statement = connection.createStatement()) {
      statement.execute("SET LOCAL session_replication_role = replica");
      statement.execute("SET LOCAL session_replication_role = origin");
      connection.rollback();
    } finally {
      try {
        connection.setAutoCommit(previousAutoCommit);
      } catch (SQLException ignored) {
        // The connection is closed by the caller immediately afterwards.
      }
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
