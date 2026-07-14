package org.congcong.algomentor.api.admin.audit;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class AdminOperationAuditWriteExecutor {

  private final AdminOperationAuditMapper mapper;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public AdminOperationAuditWriteExecutor(
      AdminOperationAuditMapper mapper,
      PlatformTransactionManager transactionManager,
      Clock clock
  ) {
    this.mapper = mapper;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void write(AdminOperationAuditEvent event, String requestId, String metadataJson) {
    transactionTemplate.executeWithoutResult(status -> mapper.insert(
        event.operatorUserId(),
        event.action().name(),
        event.targetType().name(),
        event.targetRef(),
        event.outcome().name(),
        requestId,
        metadataJson,
        Instant.now(clock)));
  }
}
