package org.congcong.algomentor.api.databasebackup.postgres;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Executes PostgreSQL client processes without a shell. */
public interface DatabaseBackupProcessRunner {

  ProcessResult run(List<String> command, Map<String, String> environment, Path workingDirectory);

  record ProcessResult(int exitCode, boolean timedOut, String output) {
    public boolean succeeded() {
      return !timedOut && exitCode == 0;
    }
  }
}
