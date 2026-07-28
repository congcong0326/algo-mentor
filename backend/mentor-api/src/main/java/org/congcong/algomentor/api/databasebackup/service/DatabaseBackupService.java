package org.congcong.algomentor.api.databasebackup.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.congcong.algomentor.api.databasebackup.config.DatabaseBackupProperties;
import org.congcong.algomentor.api.databasebackup.model.DatabaseBackupManifest;
import org.congcong.algomentor.api.databasebackup.model.DatabaseRestoreResponse;
import org.congcong.algomentor.api.databasebackup.postgres.DatabaseBackupMetadata;
import org.congcong.algomentor.api.databasebackup.postgres.JdbcDatabaseBackupMetadataRepository;
import org.congcong.algomentor.api.databasebackup.postgres.PostgresDatabaseBackupClient;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;

/** Coordinates archive generation and all-or-nothing PostgreSQL data recovery. */
public final class DatabaseBackupService {

  public static final int FORMAT_VERSION = 1;
  public static final String APPLICATION = "algo-mentor";
  public static final String SCHEMA = "public";
  public static final String ARCHIVE_EXTENSION = ".ambak";
  public static final String MANIFEST_ENTRY = "manifest.json";
  public static final String DUMP_ENTRY = "data.dump";
  public static final String OVERWRITE_CONFIRMATION = "OVERWRITE_ALL_DATA";

  private static final int BUFFER_SIZE = 16_384;
  private static final int MAX_MANIFEST_BYTES = 65_536;
  private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC);
  private static final String AUDIT_TARGET_REF = "public";

  private final DatabaseBackupProperties properties;
  private final JdbcDatabaseBackupMetadataRepository metadataRepository;
  private final PostgresDatabaseBackupClient postgresClient;
  private final ObjectMapper objectMapper;
  private final DatabaseRestoreCacheInvalidator cacheInvalidator;
  private final AdminOperationAuditRecorder auditRecorder;
  private final DatabaseBackupMetrics metrics;
  private final Clock clock;
  private final Semaphore operationMutex = new Semaphore(1);

  public DatabaseBackupService(
      DatabaseBackupProperties properties,
      JdbcDatabaseBackupMetadataRepository metadataRepository,
      PostgresDatabaseBackupClient postgresClient,
      ObjectMapper objectMapper,
      DatabaseRestoreCacheInvalidator cacheInvalidator,
      AdminOperationAuditRecorder auditRecorder,
      DatabaseBackupMetrics metrics,
      Clock clock
  ) {
    this.properties = Objects.requireNonNull(properties, "properties must not be null");
    this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository must not be null");
    this.postgresClient = Objects.requireNonNull(postgresClient, "postgresClient must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.cacheInvalidator = Objects.requireNonNull(cacheInvalidator, "cacheInvalidator must not be null");
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Produces an archive before the response body starts, while preserving a single operation lock
   * until its streamed response has finished.
   */
  public PreparedDatabaseBackup prepareBackup(long operatorUserId) {
    acquireOperation();
    long startedAt = System.nanoTime();
    Path temporaryDirectory = null;
    try {
      requireEnabled();
      Instant createdAt = Instant.now(clock);
      DatabaseBackupMetadata metadata = metadataRepository.readCurrent();
      temporaryDirectory = Files.createTempDirectory("algo-mentor-database-backup-");
      Path dump = temporaryDirectory.resolve(DUMP_ENTRY);
      postgresClient.dumpData(dump, temporaryDirectory);
      long dumpSize = Files.size(dump);
      if (dumpSize < 1) {
        throw new DatabaseBackupException(
            DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
            "PostgreSQL data export produced an empty dump.");
      }
      DatabaseBackupManifest manifest = new DatabaseBackupManifest(
          FORMAT_VERSION,
          APPLICATION,
          metadata.applicationVersion(),
          createdAt,
          metadata.postgresMajorVersion(),
          SCHEMA,
          metadata.flywayFingerprint(),
          metadata.tables(),
          metadata.tables().size(),
          dumpSize,
          sha256(dump));
      Path archive = temporaryDirectory.resolve("database-backup" + ARCHIVE_EXTENSION);
      writeArchive(archive, dump, manifest);
      recordBackupSuccess(operatorUserId, manifest);
      metrics.record("backup", "success", startedAt);
      return new PreparedDatabaseBackup(
          archive,
          temporaryDirectory,
          "algo-mentor-data-" + FILENAME_TIMESTAMP.format(createdAt) + ARCHIVE_EXTENSION,
          operationMutex::release);
    } catch (RuntimeException | IOException exception) {
      recordFailure(operatorUserId, AdminAuditAction.DATABASE_BACKUP_EXPORT, errorCode(exception));
      metrics.record("backup", "failure", startedAt);
      if (temporaryDirectory != null) {
        deleteTemporaryDirectory(temporaryDirectory);
      }
      operationMutex.release();
      if (exception instanceof DatabaseBackupException databaseBackupException) {
        throw databaseBackupException;
      }
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Unable to create database backup archive.",
          exception);
    }
  }

  public DatabaseRestoreResponse restore(
      long operatorUserId,
      String confirmation,
      InputStream archiveInput,
      long declaredUploadSize
  ) {
    if (!OVERWRITE_CONFIRMATION.equals(confirmation)) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID,
          "Full data overwrite confirmation is required.");
    }
    acquireOperation();
    long startedAt = System.nanoTime();
    Path temporaryDirectory = null;
    try {
      requireEnabled();
      validateDeclaredUploadSize(declaredUploadSize);
      temporaryDirectory = Files.createTempDirectory("algo-mentor-database-restore-");
      Path archive = temporaryDirectory.resolve("uploaded" + ARCHIVE_EXTENSION);
      copyUpload(archiveInput, archive, properties.getMaxUploadSize().toBytes());
      RestorableArchive restorableArchive = unpackArchive(archive, temporaryDirectory);
      DatabaseBackupMetadata current = metadataRepository.readCurrent();
      validateCompatibility(restorableArchive.manifest(), current);
      postgresClient.verifyDataOnlyArchive(restorableArchive.dump(), temporaryDirectory);
      metadataRepository.verifyRestorePrivileges();
      postgresClient.restoreData(restorableArchive.dump(), current.tables(), temporaryDirectory);
      cacheInvalidator.invalidateAll();
      Instant restoredAt = Instant.now(clock);
      recordRestoreSuccess(operatorUserId, restorableArchive.manifest());
      metrics.record("restore", "success", startedAt);
      return new DatabaseRestoreResponse(
          restoredAt,
          restorableArchive.manifest().tableCount(),
          restorableArchive.manifest().dumpSizeBytes(),
          true);
    } catch (RuntimeException | IOException exception) {
      recordFailure(operatorUserId, AdminAuditAction.DATABASE_RESTORE_OVERWRITE, errorCode(exception));
      metrics.record("restore", "failure", startedAt);
      if (exception instanceof DatabaseBackupException databaseBackupException) {
        throw databaseBackupException;
      }
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_FAILED,
          "Unable to restore database backup archive.",
          exception);
    } finally {
      if (temporaryDirectory != null) {
        deleteTemporaryDirectory(temporaryDirectory);
      }
      operationMutex.release();
    }
  }

  private void acquireOperation() {
    if (!operationMutex.tryAcquire()) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_BUSY,
          "Another database backup or restore operation is already running.");
    }
  }

  private void requireEnabled() {
    if (!properties.isEnabled()) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Database backup operations are disabled by configuration.");
    }
  }

  private void validateDeclaredUploadSize(long declaredUploadSize) {
    if (declaredUploadSize < 1 || declaredUploadSize > properties.getMaxUploadSize().toBytes()) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID,
          "Backup archive exceeds the configured upload size limit.");
    }
  }

  private RestorableArchive unpackArchive(Path archive, Path temporaryDirectory) throws IOException {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      validateEntries(zip);
      DatabaseBackupManifest manifest = readManifest(zip);
      validateManifestShape(manifest);
      Path dump = temporaryDirectory.resolve(DUMP_ENTRY);
      ZipEntry dumpEntry = zip.getEntry(DUMP_ENTRY);
      long maxDumpBytes = maximumExtractedDumpBytes();
      if (dumpEntry.getSize() > maxDumpBytes) {
        throw archiveInvalid("Backup dump exceeds the configured extraction limit.");
      }
      try (InputStream input = zip.getInputStream(dumpEntry);
          OutputStream output = Files.newOutputStream(dump, StandardOpenOption.CREATE_NEW)) {
        copyWithLimit(input, output, maxDumpBytes);
      }
      long dumpSize = Files.size(dump);
      if (dumpSize != manifest.dumpSizeBytes()) {
        throw archiveInvalid("Backup dump size does not match manifest.");
      }
      if (!manifest.dumpSha256().equals(sha256(dump))) {
        throw new DatabaseBackupException(
            DatabaseBackupErrorCode.DATABASE_RESTORE_CHECKSUM_MISMATCH,
            "Backup dump checksum does not match manifest.");
      }
      return new RestorableArchive(manifest, dump);
    } catch (java.util.zip.ZipException exception) {
      throw archiveInvalid("Backup archive is not a valid ZIP file.", exception);
    }
  }

  private void validateEntries(ZipFile zip) {
    Set<String> names = new LinkedHashSet<>();
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      if (entry.isDirectory() || !names.add(entry.getName())) {
        throw archiveInvalid("Backup archive contains invalid ZIP entries.");
      }
    }
    if (!names.equals(Set.of(MANIFEST_ENTRY, DUMP_ENTRY))) {
      throw archiveInvalid("Backup archive must contain only manifest.json and data.dump.");
    }
  }

  private DatabaseBackupManifest readManifest(ZipFile zip) throws IOException {
    ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);
    if (manifestEntry.getSize() > MAX_MANIFEST_BYTES) {
      throw archiveInvalid("Backup manifest is too large.");
    }
    byte[] bytes;
    try (InputStream input = zip.getInputStream(manifestEntry)) {
      bytes = readWithLimit(input, MAX_MANIFEST_BYTES);
    }
    try {
      return objectMapper.readerFor(DatabaseBackupManifest.class)
          .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .readValue(bytes);
    } catch (IOException | RuntimeException exception) {
      throw archiveInvalid("Backup manifest is invalid.", exception);
    }
  }

  private static void validateManifestShape(DatabaseBackupManifest manifest) {
    if (manifest.formatVersion() != FORMAT_VERSION || !APPLICATION.equals(manifest.application())
        || !SCHEMA.equals(manifest.schema()) || isBlank(manifest.applicationVersion())
        || isBlank(manifest.flywayFingerprint()) || manifest.postgresMajorVersion() < 1
        || manifest.createdAt() == null || manifest.dumpSizeBytes() < 1
        || !isSha256(manifest.dumpSha256()) || manifest.tableCount() != manifest.tables().size()
        || manifest.tables().isEmpty() || !isSortedDistinct(manifest.tables())) {
      throw archiveInvalid("Backup manifest does not meet the required format.");
    }
    for (String table : manifest.tables()) {
      if (isBlank(table) || table.length() > 63 || containsControlCharacter(table)) {
        throw archiveInvalid("Backup manifest includes an invalid table name.");
      }
    }
  }

  private static void validateCompatibility(
      DatabaseBackupManifest manifest,
      DatabaseBackupMetadata current
  ) {
    if (!manifest.applicationVersion().equals(current.applicationVersion())
        || manifest.postgresMajorVersion() != current.postgresMajorVersion()
        || !manifest.flywayFingerprint().equals(current.flywayFingerprint())) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_VERSION_MISMATCH,
          "Backup application, PostgreSQL, or Flyway version does not match this environment.");
    }
    if (!manifest.tables().equals(current.tables())) {
      throw new DatabaseBackupException(
          DatabaseBackupErrorCode.DATABASE_RESTORE_TABLE_SET_MISMATCH,
          "Backup table set does not match the current public schema.");
    }
  }

  private void writeArchive(Path archive, Path dump, DatabaseBackupManifest manifest) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
      zip.write(objectMapper.writeValueAsBytes(manifest));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry(DUMP_ENTRY));
      Files.copy(dump, zip);
      zip.closeEntry();
    }
  }

  private static void copyUpload(InputStream input, Path target, long maximumBytes) throws IOException {
    if (input == null) {
      throw archiveInvalid("Backup archive is missing.");
    }
    try (InputStream source = input;
        OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
      copyWithLimit(source, output, maximumBytes);
    }
  }

  private static void copyWithLimit(InputStream input, OutputStream output, long maximumBytes) throws IOException {
    byte[] buffer = new byte[BUFFER_SIZE];
    long total = 0;
    for (int read; (read = input.read(buffer)) >= 0;) {
      total = Math.addExact(total, read);
      if (total > maximumBytes) {
        throw archiveInvalid("Backup archive exceeds the configured size limit.");
      }
      output.write(buffer, 0, read);
    }
  }

  private static byte[] readWithLimit(InputStream input, int maximumBytes) throws IOException {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    copyWithLimit(input, output, maximumBytes);
    return output.toByteArray();
  }

  private long maximumExtractedDumpBytes() {
    try {
      return Math.multiplyExact(properties.getMaxUploadSize().toBytes(), 4);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private static String sha256(Path file) throws IOException {
    MessageDigest digest = sha256Digest();
    try (InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[BUFFER_SIZE];
      for (int read; (read = input.read(buffer)) >= 0;) {
        digest.update(buffer, 0, read);
      }
    }
    return "sha256:" + HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void recordBackupSuccess(long operatorUserId, DatabaseBackupManifest manifest) {
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.DATABASE_BACKUP_EXPORT,
        AdminAuditTargetType.DATABASE_BACKUP,
        AUDIT_TARGET_REF,
        Map.of(
            AdminAuditMetadataKey.APPLICATION_VERSION, manifest.applicationVersion(),
            AdminAuditMetadataKey.TABLE_COUNT, manifest.tableCount(),
            AdminAuditMetadataKey.DUMP_SIZE_BYTES, manifest.dumpSizeBytes())));
  }

  private void recordRestoreSuccess(long operatorUserId, DatabaseBackupManifest manifest) {
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.DATABASE_RESTORE_OVERWRITE,
        AdminAuditTargetType.DATABASE_BACKUP,
        AUDIT_TARGET_REF,
        Map.of(
            AdminAuditMetadataKey.APPLICATION_VERSION, manifest.applicationVersion(),
            AdminAuditMetadataKey.TABLE_COUNT, manifest.tableCount(),
            AdminAuditMetadataKey.DUMP_SIZE_BYTES, manifest.dumpSizeBytes())));
  }

  private void recordFailure(long operatorUserId, AdminAuditAction action, String errorCode) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        action,
        AdminAuditTargetType.DATABASE_BACKUP,
        AUDIT_TARGET_REF,
        errorCode));
  }

  private static String errorCode(Exception exception) {
    return exception instanceof DatabaseBackupException databaseBackupException
        ? databaseBackupException.code().name()
        : DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED.name();
  }

  static void deleteTemporaryDirectory(Path directory) {
    try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(DatabaseBackupService::deleteIfExists);
    } catch (IOException ignored) {
      // Temporary files have no persistent business value and are retried by the operating system cleanup.
    }
  }

  private static void deleteIfExists(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best effort cleanup avoids masking the actual backup or restore result.
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static boolean isSha256(String value) {
    return value != null && value.matches("sha256:[0-9a-f]{64}");
  }

  private static boolean isSortedDistinct(List<String> values) {
    List<String> sorted = new ArrayList<>(values);
    sorted.sort(String::compareTo);
    return sorted.equals(values) && new LinkedHashSet<>(values).size() == values.size();
  }

  private static boolean containsControlCharacter(String value) {
    return value.chars().anyMatch(character -> Character.isISOControl(character));
  }

  private static DatabaseBackupException archiveInvalid(String message) {
    return new DatabaseBackupException(DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID, message);
  }

  private static DatabaseBackupException archiveInvalid(String message, Throwable cause) {
    return new DatabaseBackupException(DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID, message, cause);
  }

  private record RestorableArchive(DatabaseBackupManifest manifest, Path dump) {
  }
}
