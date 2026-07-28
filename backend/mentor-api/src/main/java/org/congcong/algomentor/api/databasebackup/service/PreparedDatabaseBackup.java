package org.congcong.algomentor.api.databasebackup.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** A generated temporary archive whose cleanup also releases the operation mutex. */
public final class PreparedDatabaseBackup implements AutoCloseable {

  private final Path archive;
  private final Path temporaryDirectory;
  private final String filename;
  private final Runnable release;
  private final AtomicBoolean closed = new AtomicBoolean();

  PreparedDatabaseBackup(Path archive, Path temporaryDirectory, String filename, Runnable release) {
    this.archive = Objects.requireNonNull(archive, "archive must not be null");
    this.temporaryDirectory = Objects.requireNonNull(temporaryDirectory, "temporaryDirectory must not be null");
    this.filename = Objects.requireNonNull(filename, "filename must not be null");
    this.release = Objects.requireNonNull(release, "release must not be null");
  }

  public String filename() {
    return filename;
  }

  public void writeTo(OutputStream output) throws IOException {
    try {
      Files.copy(archive, output);
    } finally {
      close();
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    DatabaseBackupService.deleteTemporaryDirectory(temporaryDirectory);
    release.run();
  }
}
