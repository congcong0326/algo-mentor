package org.congcong.algomentor.api.databasebackup.postgres;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.api.databasebackup.config.DatabaseBackupProperties;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;

/** PostgreSQL client command construction and restore-script generation. */
public class PostgresDatabaseBackupClient {

  private final DatabaseBackupProperties properties;
  private final DatabaseBackupProcessRunner processRunner;
  private final PostgresConnectionDetails connection;

  public PostgresDatabaseBackupClient(
      DatabaseBackupProperties properties,
      DatabaseBackupProcessRunner processRunner,
      PostgresConnectionDetails connection
  ) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
    this.connection = Objects.requireNonNull(connection, "connection must not be null");
  }

  public void dumpData(Path dumpFile, Path workingDirectory) {
    List<String> command = List.of(
        requiredCommand(properties.getPgDumpPath(), "pgDumpPath"),
        "--format=custom",
        "--data-only",
        "--no-owner",
        "--no-privileges",
        "--schema=public",
        "--file=" + dumpFile,
        "--dbname=" + connection.connectionUri());
    requireSuccess(processRunner.run(command, connection.processEnvironment(), workingDirectory),
        DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
        "PostgreSQL data export failed.");
  }

  public void verifyDataOnlyArchive(Path dumpFile, Path workingDirectory) {
    List<String> command = List.of(
        requiredCommand(properties.getPgRestorePath(), "pgRestorePath"),
        "--list",
        dumpFile.toString());
    DatabaseBackupProcessRunner.ProcessResult result = processRunner.run(
        command, connection.processEnvironment(), workingDirectory);
    requireSuccess(result, DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID,
        "Backup data dump cannot be read by pg_restore.");
    for (String line : result.output().split("\\R")) {
      String entry = archiveEntry(line);
      if (entry != null && !isDataOnlyEntry(entry)) {
        throw new DatabaseBackupException(
            DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID,
            "Backup data dump contains a non-data archive entry.");
      }
    }
  }

  public void restoreData(
      Path dumpFile,
      List<String> tables,
      Path workingDirectory
  ) {
    Path generatedSql = workingDirectory.resolve("restore-data.sql");
    Path restoreScript = workingDirectory.resolve("restore-transaction.sql");
    List<String> pgRestoreCommand = List.of(
        requiredCommand(properties.getPgRestorePath(), "pgRestorePath"),
        "--data-only",
        "--no-owner",
        "--no-privileges",
        "--schema=public",
        "--file=" + generatedSql,
        dumpFile.toString());
    requireSuccess(processRunner.run(pgRestoreCommand, connection.processEnvironment(), workingDirectory),
        DatabaseBackupErrorCode.DATABASE_RESTORE_FAILED,
        "Unable to generate PostgreSQL restore statements.");
    try {
      writeRestoreScript(restoreScript, generatedSql, tables);
    } catch (IOException exception) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_FAILED,
          "Unable to prepare PostgreSQL restore transaction.",
          exception);
    }
    List<String> psqlCommand = List.of(
        requiredCommand(properties.getPsqlPath(), "psqlPath"),
        "--no-psqlrc",
        "--set=ON_ERROR_STOP=1",
        "--dbname=" + connection.connectionUri(),
        "--file=" + restoreScript);
    requireSuccess(processRunner.run(psqlCommand, connection.processEnvironment(), workingDirectory),
        DatabaseBackupErrorCode.DATABASE_RESTORE_FAILED,
        "PostgreSQL restore transaction failed.");
  }

  private static void writeRestoreScript(Path restoreScript, Path generatedSql, List<String> tables)
      throws IOException {
    if (tables == null || tables.isEmpty()) {
      throw new IOException("No public tables are available for restore");
    }
    try (OutputStream output = Files.newOutputStream(restoreScript)) {
      output.write("BEGIN;\nSET LOCAL session_replication_role = replica;\n".getBytes(StandardCharsets.UTF_8));
      output.write(("TRUNCATE TABLE " + quotedTableList(tables) + " RESTART IDENTITY CASCADE;\n")
          .getBytes(StandardCharsets.UTF_8));
      Files.copy(generatedSql, output);
      output.write("\nSET LOCAL session_replication_role = origin;\nCOMMIT;\n".getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String quotedTableList(List<String> tables) {
    List<String> quoted = new ArrayList<>(tables.size());
    for (String table : tables) {
      if (table == null || table.isBlank()) {
        throw new IllegalArgumentException("table name must not be blank");
      }
      quoted.add("public.\"" + table.replace("\"", "\"\"") + "\"");
    }
    return String.join(", ", quoted);
  }

  private static String archiveEntry(String line) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.startsWith(";")) {
      return null;
    }
    int delimiter = trimmed.indexOf(';');
    if (delimiter < 0) {
      return trimmed;
    }
    String tocEntry = trimmed.substring(delimiter + 1).trim();
    return tocEntry.replaceFirst("^\\d+\\s+\\d+\\s+", "");
  }

  private static boolean isDataOnlyEntry(String entry) {
    return entry.startsWith("TABLE DATA public ") || entry.startsWith("SEQUENCE SET public ");
  }

  private static void requireSuccess(
      DatabaseBackupProcessRunner.ProcessResult result,
      DatabaseBackupErrorCode code,
      String message
  ) {
    if (!result.succeeded()) {
      throw new DatabaseBackupException(code, message);
    }
  }

  private static String requiredCommand(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          name + " must be configured.");
    }
    return value;
  }
}
