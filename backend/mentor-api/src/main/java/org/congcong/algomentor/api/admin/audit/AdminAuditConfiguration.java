package org.congcong.algomentor.api.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({SqlSessionTemplate.class, PlatformTransactionManager.class})
public class AdminAuditConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AdminOperationAuditMapper adminOperationAuditMapper(SqlSessionTemplate sqlSessionTemplate) {
    return sqlSessionTemplate.getMapper(AdminOperationAuditMapper.class);
  }

  @Bean
  @ConditionalOnMissingBean
  public AdminOperationAuditWriteExecutor adminOperationAuditWriteExecutor(
      AdminOperationAuditMapper mapper,
      PlatformTransactionManager transactionManager,
      Clock clock
  ) {
    return new AdminOperationAuditWriteExecutor(mapper, transactionManager, clock);
  }

  @Bean
  @ConditionalOnMissingBean(AdminOperationAuditRecorder.class)
  public AdminOperationAuditRecorder adminOperationAuditRecorder(
      AdminOperationAuditWriteExecutor writeExecutor,
      ObjectMapper objectMapper,
      ObjectProvider<MeterRegistry> meterRegistryProvider
  ) {
    return new PostgresAdminOperationAuditRecorder(
        writeExecutor,
        objectMapper,
        meterRegistryProvider.getIfAvailable());
  }
}
