package org.congcong.algomentor.api.databasebackup.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.api.databasebackup.config.DatabaseBackupProperties;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostgresDatabaseBackupClientTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  void dumpPassesPasswordOnlyThroughProcessEnvironment() {
    RecordingProcessRunner runner = new RecordingProcessRunner();
    PostgresDatabaseBackupClient client = client(runner);
    Path dump = temporaryDirectory.resolve("data.dump");

    client.dumpData(dump, temporaryDirectory);

    assertThat(runner.commands()).hasSize(1);
    assertThat(runner.commands().get(0)).contains("--data-only", "--no-owner", "--no-privileges");
    assertThat(runner.commands().get(0)).noneMatch(value -> value.contains("super-secret"));
    assertThat(runner.environments().get(0)).containsEntry("PGPASSWORD", "super-secret");
    assertThat(runner.environments().get(0)).containsEntry("PGUSER", "backup_user");
  }

  @Test
  void restoreWritesACompleteSingleTransactionScript() throws Exception {
    RecordingProcessRunner runner = new RecordingProcessRunner();
    runner.onRun((command, environment, workingDirectory) -> {
      if (command.get(0).equals("pg_restore") && command.stream().anyMatch(value -> value.startsWith("--file="))) {
        Path generatedSql = Path.of(command.stream().filter(value -> value.startsWith("--file=")).findFirst().orElseThrow()
            .substring("--file=".length()));
        try {
          Files.writeString(generatedSql, "COPY public.example_table FROM stdin;\n\\.\n");
        } catch (Exception exception) {
          throw new IllegalStateException(exception);
        }
      }
      return new DatabaseBackupProcessRunner.ProcessResult(0, false, "");
    });
    PostgresDatabaseBackupClient client = client(runner);
    Path dump = temporaryDirectory.resolve("data.dump");
    Files.writeString(dump, "custom-format-placeholder");

    client.restoreData(dump, List.of("example_table", "quoted\"table"), temporaryDirectory);

    List<String> psqlCommand = runner.commands().stream()
        .filter(command -> command.get(0).equals("psql"))
        .findFirst()
        .orElseThrow();
    Path script = Path.of(psqlCommand.stream().filter(value -> value.startsWith("--file=")).findFirst().orElseThrow()
        .substring("--file=".length()));
    String sql = Files.readString(script);
    assertThat(sql).startsWith("BEGIN;\nSET LOCAL session_replication_role = replica;")
        .contains("TRUNCATE TABLE public.\"example_table\", public.\"quoted\"\"table\" RESTART IDENTITY CASCADE;")
        .contains("COPY public.example_table FROM stdin;")
        .endsWith("SET LOCAL session_replication_role = origin;\nCOMMIT;\n");
    assertThat(psqlCommand).contains("--set=ON_ERROR_STOP=1", "--no-psqlrc");
  }

  @Test
  void rejectsArchiveListsThatContainSchemaObjects() {
    RecordingProcessRunner runner = new RecordingProcessRunner();
    runner.onRun((command, environment, workingDirectory) ->
        new DatabaseBackupProcessRunner.ProcessResult(0, false,
            "; Archive created\n1; 0 0 TABLE public forbidden backup_user\n"));
    PostgresDatabaseBackupClient client = client(runner);

    assertThatThrownBy(() -> client.verifyDataOnlyArchive(
        temporaryDirectory.resolve("data.dump"), temporaryDirectory))
        .isInstanceOf(DatabaseBackupException.class)
        .extracting(error -> ((DatabaseBackupException) error).code())
        .isEqualTo(DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID);
  }

  @Test
  void acceptsDataOnlyArchiveListEntriesWithObjectIdentifiers() {
    RecordingProcessRunner runner = new RecordingProcessRunner();
    runner.onRun((command, environment, workingDirectory) ->
        new DatabaseBackupProcessRunner.ProcessResult(0, false,
            "4031; 0 17550 TABLE DATA public auth_users backup_user\n"
                + "4132; 0 0 SEQUENCE SET public auth_users_id_seq backup_user\n"));
    PostgresDatabaseBackupClient client = client(runner);

    client.verifyDataOnlyArchive(temporaryDirectory.resolve("data.dump"), temporaryDirectory);
  }

  @Test
  void rejectsCredentialsEmbeddedInJdbcUrl() {
    assertThatThrownBy(() -> PostgresConnectionDetails.fromJdbcUrl(
        "jdbc:postgresql://user:password@localhost/algo_mentor", "backup_user", "super-secret"))
        .isInstanceOf(DatabaseBackupException.class)
        .extracting(error -> ((DatabaseBackupException) error).code())
        .isEqualTo(DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED);
  }

  private static PostgresDatabaseBackupClient client(RecordingProcessRunner runner) {
    DatabaseBackupProperties properties = new DatabaseBackupProperties();
    properties.setCommandTimeout(Duration.ofSeconds(5));
    return new PostgresDatabaseBackupClient(
        properties,
        runner,
        PostgresConnectionDetails.fromJdbcUrl(
            "jdbc:postgresql://localhost:5432/algo_mentor?sslmode=disable",
            "backup_user",
            "super-secret"));
  }

  private static final class RecordingProcessRunner implements DatabaseBackupProcessRunner {
    private final List<List<String>> commands = new ArrayList<>();
    private final List<Map<String, String>> environments = new ArrayList<>();
    private RunHandler handler = (command, environment, workingDirectory) ->
        new ProcessResult(0, false, "");

    @Override
    public ProcessResult run(List<String> command, Map<String, String> environment, Path workingDirectory) {
      commands.add(List.copyOf(command));
      environments.add(Map.copyOf(environment));
      return handler.run(command, environment, workingDirectory);
    }

    void onRun(RunHandler handler) {
      this.handler = handler;
    }

    List<List<String>> commands() {
      return commands;
    }

    List<Map<String, String>> environments() {
      return environments;
    }
  }

  @FunctionalInterface
  private interface RunHandler {
    DatabaseBackupProcessRunner.ProcessResult run(
        List<String> command,
        Map<String, String> environment,
        Path workingDirectory);
  }
}
