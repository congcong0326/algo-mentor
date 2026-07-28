package org.congcong.algomentor.api.databasebackup.postgres;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;

/** System implementation with bounded diagnostic capture and command timeouts. */
public final class SystemDatabaseBackupProcessRunner implements DatabaseBackupProcessRunner {

  private static final int MAX_OUTPUT_CHARS = 16_384;
  private final Duration timeout;

  public SystemDatabaseBackupProcessRunner(Duration timeout) {
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("command timeout must be positive");
    }
  }

  @Override
  public ProcessResult run(List<String> command, Map<String, String> environment, Path workingDirectory) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(environment, "environment must not be null");
    Path output = null;
    try {
      output = Files.createTempFile(workingDirectory, "postgres-client-", ".log");
      ProcessBuilder builder = new ProcessBuilder(command);
      builder.directory(workingDirectory.toFile());
      builder.redirectErrorStream(true);
      builder.redirectOutput(output.toFile());
      Map<String, String> processEnvironment = builder.environment();
      processEnvironment.remove("PGPASSWORD");
      processEnvironment.remove("PGUSER");
      processEnvironment.remove("PGSERVICE");
      processEnvironment.putAll(environment);
      Process process = builder.start();
      boolean completed = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroy();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
          process.destroyForcibly();
          process.waitFor();
        }
      }
      return new ProcessResult(
          completed ? process.exitValue() : -1,
          !completed,
          readOutput(output));
    } catch (IOException exception) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Unable to start a PostgreSQL client command.",
          exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "PostgreSQL client command was interrupted.",
          exception);
    } finally {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException ignored) {
          // The surrounding request temporary directory is removed by the caller.
        }
      }
    }
  }

  private static String readOutput(Path output) throws IOException {
    byte[] bytes = Files.readAllBytes(output);
    int length = Math.min(bytes.length, MAX_OUTPUT_CHARS);
    return new String(bytes, 0, length, StandardCharsets.UTF_8);
  }
}
