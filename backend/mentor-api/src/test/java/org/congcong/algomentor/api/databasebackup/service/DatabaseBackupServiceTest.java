package org.congcong.algomentor.api.databasebackup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.congcong.algomentor.api.databasebackup.config.DatabaseBackupProperties;
import org.congcong.algomentor.api.databasebackup.model.DatabaseBackupManifest;
import org.congcong.algomentor.api.databasebackup.model.DatabaseRestoreResponse;
import org.congcong.algomentor.api.databasebackup.postgres.DatabaseBackupMetadata;
import org.congcong.algomentor.api.databasebackup.postgres.JdbcDatabaseBackupMetadataRepository;
import org.congcong.algomentor.api.databasebackup.postgres.PostgresDatabaseBackupClient;
import org.congcong.algomentor.cache.api.LocalCacheRegion;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseBackupServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");
  private static final DatabaseBackupMetadata METADATA = new DatabaseBackupMetadata(
      "0.1.0-SNAPSHOT", 16, "sha256:flyway", List.of("auth_users", "flyway_schema_history"));

  @TempDir
  Path temporaryDirectory;

  @Test
  void createsStrictTwoEntryArchiveAndKeepsOperationExclusive() throws Exception {
    JdbcDatabaseBackupMetadataRepository metadataRepository = mock(JdbcDatabaseBackupMetadataRepository.class);
    PostgresDatabaseBackupClient postgresClient = mock(PostgresDatabaseBackupClient.class);
    when(metadataRepository.readCurrent()).thenReturn(METADATA);
    doAnswer(invocation -> {
      Path dump = invocation.getArgument(0);
      Files.writeString(dump, "custom-data-dump", StandardCharsets.UTF_8);
      return null;
    }).when(postgresClient).dumpData(any(Path.class), any(Path.class));
    DatabaseBackupService service = service(metadataRepository, postgresClient, new CacheRegionRegistry());

    PreparedDatabaseBackup prepared = service.prepareBackup(42);
    assertThatThrownBy(() -> service.prepareBackup(42))
        .isInstanceOf(DatabaseBackupException.class)
        .extracting(error -> ((DatabaseBackupException) error).code())
        .isEqualTo(DatabaseBackupErrorCode.DATABASE_BACKUP_BUSY);

    ByteArrayOutputStream archive = new ByteArrayOutputStream();
    prepared.writeTo(archive);

    assertThat(zipEntryNames(archive.toByteArray())).containsExactly(
        DatabaseBackupService.MANIFEST_ENTRY,
        DatabaseBackupService.DUMP_ENTRY);
    DatabaseBackupManifest manifest = manifest(archive.toByteArray());
    assertThat(manifest.application()).isEqualTo(DatabaseBackupService.APPLICATION);
    assertThat(manifest.createdAt()).isEqualTo(NOW);
    assertThat(manifest.tables()).containsExactlyElementsOf(METADATA.tables());
    assertThat(manifest.dumpSha256()).startsWith("sha256:");
  }

  @Test
  void rejectsVersionMismatchBeforeCallingPostgresRestore() throws Exception {
    JdbcDatabaseBackupMetadataRepository metadataRepository = mock(JdbcDatabaseBackupMetadataRepository.class);
    PostgresDatabaseBackupClient postgresClient = mock(PostgresDatabaseBackupClient.class);
    when(metadataRepository.readCurrent()).thenReturn(METADATA);
    DatabaseBackupService service = service(metadataRepository, postgresClient, new CacheRegionRegistry());
    byte[] archive = archive(new DatabaseBackupManifest(
        DatabaseBackupService.FORMAT_VERSION,
        DatabaseBackupService.APPLICATION,
        "0.2.0-SNAPSHOT",
        NOW,
        METADATA.postgresMajorVersion(),
        DatabaseBackupService.SCHEMA,
        METADATA.flywayFingerprint(),
        METADATA.tables(),
        METADATA.tables().size(),
        0,
        ""), "custom-data-dump");

    assertThatThrownBy(() -> service.restore(
        42,
        DatabaseBackupService.OVERWRITE_CONFIRMATION,
        new ByteArrayInputStream(archive),
        archive.length))
        .isInstanceOf(DatabaseBackupException.class)
        .extracting(error -> ((DatabaseBackupException) error).code())
        .isEqualTo(DatabaseBackupErrorCode.DATABASE_RESTORE_VERSION_MISMATCH);
    verify(postgresClient, never()).verifyDataOnlyArchive(any(Path.class), any(Path.class));
    verify(postgresClient, never()).restoreData(any(Path.class), anyList(), any(Path.class));
  }

  @Test
  void restoresMatchingArchiveAndInvalidatesAllRegisteredCaches() throws Exception {
    JdbcDatabaseBackupMetadataRepository metadataRepository = mock(JdbcDatabaseBackupMetadataRepository.class);
    PostgresDatabaseBackupClient postgresClient = mock(PostgresDatabaseBackupClient.class);
    when(metadataRepository.readCurrent()).thenReturn(METADATA);
    doNothing().when(metadataRepository).verifyRestorePrivileges();
    doNothing().when(postgresClient).verifyDataOnlyArchive(any(Path.class), any(Path.class));
    doNothing().when(postgresClient).restoreData(any(Path.class), anyList(), any(Path.class));
    CacheRegionRegistry cacheRegistry = new CacheRegionRegistry();
    RecordingCacheRegion cache = new RecordingCacheRegion();
    cacheRegistry.registerLocalRegion(new CacheRegionName("database-backup-test"), cache);
    DatabaseBackupService service = service(metadataRepository, postgresClient, cacheRegistry);
    byte[] archive = archive(manifestFor("custom-data-dump"), "custom-data-dump");

    DatabaseRestoreResponse response = service.restore(
        42,
        DatabaseBackupService.OVERWRITE_CONFIRMATION,
        new ByteArrayInputStream(archive),
        archive.length);

    assertThat(response.loginRequired()).isTrue();
    assertThat(response.tableCount()).isEqualTo(METADATA.tables().size());
    assertThat(cache.invalidated()).isTrue();
    verify(metadataRepository).verifyRestorePrivileges();
    verify(postgresClient).verifyDataOnlyArchive(any(Path.class), any(Path.class));
    verify(postgresClient).restoreData(any(Path.class), anyList(), any(Path.class));
  }

  private DatabaseBackupService service(
      JdbcDatabaseBackupMetadataRepository metadataRepository,
      PostgresDatabaseBackupClient postgresClient,
      CacheRegionRegistry cacheRegistry
  ) {
    DatabaseBackupProperties properties = new DatabaseBackupProperties();
    properties.setEnabled(true);
    properties.setMaxUploadSize(org.springframework.util.unit.DataSize.ofMegabytes(4));
    return new DatabaseBackupService(
        properties,
        metadataRepository,
        postgresClient,
        objectMapper(),
        new DatabaseRestoreCacheInvalidator(cacheRegistry),
        new NoopAdminOperationAuditRecorder(),
        new DatabaseBackupMetrics(null),
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private DatabaseBackupManifest manifestFor(String dump) {
    return new DatabaseBackupManifest(
        DatabaseBackupService.FORMAT_VERSION,
        DatabaseBackupService.APPLICATION,
        METADATA.applicationVersion(),
        NOW,
        METADATA.postgresMajorVersion(),
        DatabaseBackupService.SCHEMA,
        METADATA.flywayFingerprint(),
        METADATA.tables(),
        METADATA.tables().size(),
        dump.getBytes(StandardCharsets.UTF_8).length,
        "sha256:placeholder");
  }

  private byte[] archive(DatabaseBackupManifest source, String dump) throws IOException {
    byte[] dumpBytes = dump.getBytes(StandardCharsets.UTF_8);
    java.security.MessageDigest digest;
    try {
      digest = java.security.MessageDigest.getInstance("SHA-256");
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
    DatabaseBackupManifest manifest = new DatabaseBackupManifest(
        source.formatVersion(), source.application(), source.applicationVersion(), source.createdAt(),
        source.postgresMajorVersion(), source.schema(), source.flywayFingerprint(), source.tables(),
        source.tableCount(), dumpBytes.length,
        "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(dumpBytes)));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry(DatabaseBackupService.MANIFEST_ENTRY));
      zip.write(objectMapper().writeValueAsBytes(manifest));
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry(DatabaseBackupService.DUMP_ENTRY));
      zip.write(dumpBytes);
      zip.closeEntry();
    }
    return output.toByteArray();
  }

  private static ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private static List<String> zipEntryNames(byte[] archive) throws IOException {
    List<String> names = new java.util.ArrayList<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
        names.add(entry.getName());
      }
    }
    return names;
  }

  private static DatabaseBackupManifest manifest(byte[] archive) throws IOException {
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
      ZipEntry entry = zip.getNextEntry();
      assertThat(entry.getName()).isEqualTo(DatabaseBackupService.MANIFEST_ENTRY);
      return objectMapper().readValue(zip.readAllBytes(), DatabaseBackupManifest.class);
    }
  }

  private static final class RecordingCacheRegion implements LocalCacheRegion<String, String> {
    private boolean invalidated;

    @Override
    public java.util.Optional<String> getIfPresent(String key) {
      return java.util.Optional.empty();
    }

    @Override
    public String get(String key, java.util.function.Function<? super String, ? extends String> loader) {
      return loader.apply(key);
    }

    @Override
    public void put(String key, String value) {
    }

    @Override
    public void invalidate(String key) {
    }

    @Override
    public void invalidateAll() {
      invalidated = true;
    }

    boolean invalidated() {
      return invalidated;
    }
  }
}
