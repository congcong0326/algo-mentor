package org.congcong.algomentor.api.databasebackup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import org.congcong.algomentor.api.databasebackup.postgres.DatabaseBackupProcessRunner;
import org.congcong.algomentor.api.databasebackup.postgres.JdbcDatabaseBackupMetadataRepository;
import org.congcong.algomentor.api.databasebackup.postgres.PostgresConnectionDetails;
import org.congcong.algomentor.api.databasebackup.postgres.PostgresDatabaseBackupClient;
import org.congcong.algomentor.api.databasebackup.postgres.SystemDatabaseBackupProcessRunner;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupMetrics;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupService;
import org.congcong.algomentor.api.databasebackup.service.DatabaseRestoreCacheInvalidator;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.common.admin.audit.NoopAdminOperationAuditRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.datasource.url")
@EnableConfigurationProperties(DatabaseBackupProperties.class)
public class DatabaseBackupConfiguration {

  @Bean
  public DatabaseBackupProcessRunner databaseBackupProcessRunner(DatabaseBackupProperties properties) {
    return new SystemDatabaseBackupProcessRunner(properties.getCommandTimeout());
  }

  @Bean
  public JdbcDatabaseBackupMetadataRepository databaseBackupMetadataRepository(
      DataSource dataSource,
      DatabaseBackupProperties properties
  ) {
    return new JdbcDatabaseBackupMetadataRepository(dataSource, properties.getApplicationVersion());
  }

  @Bean
  public PostgresDatabaseBackupClient postgresDatabaseBackupClient(
      DatabaseBackupProperties properties,
      DatabaseBackupProcessRunner processRunner,
      @Value("${spring.datasource.url}") String databaseUrl,
      @Value("${spring.datasource.username:}") String databaseUsername,
      @Value("${spring.datasource.password:}") String databasePassword
  ) {
    return new PostgresDatabaseBackupClient(
        properties,
        processRunner,
        PostgresConnectionDetails.fromJdbcUrl(databaseUrl, databaseUsername, databasePassword));
  }

  @Bean
  public DatabaseRestoreCacheInvalidator databaseRestoreCacheInvalidator(
      ObjectProvider<CacheRegionRegistry> cacheRegistry
  ) {
    CacheRegionRegistry registry = cacheRegistry.getIfAvailable();
    return registry == null
        ? DatabaseRestoreCacheInvalidator.noop()
        : new DatabaseRestoreCacheInvalidator(registry);
  }

  @Bean
  public DatabaseBackupService databaseBackupService(
      DatabaseBackupProperties properties,
      JdbcDatabaseBackupMetadataRepository metadataRepository,
      PostgresDatabaseBackupClient postgresClient,
      ObjectMapper objectMapper,
      DatabaseRestoreCacheInvalidator cacheInvalidator,
      ObjectProvider<AdminOperationAuditRecorder> auditRecorder,
      ObjectProvider<MeterRegistry> meterRegistry,
      ObjectProvider<Clock> clock
  ) {
    return new DatabaseBackupService(
        properties,
        metadataRepository,
        postgresClient,
        objectMapper,
        cacheInvalidator,
        auditRecorder.getIfAvailable(NoopAdminOperationAuditRecorder::new),
        new DatabaseBackupMetrics(meterRegistry.getIfAvailable()),
        clock.getIfAvailable(Clock::systemUTC));
  }
}
