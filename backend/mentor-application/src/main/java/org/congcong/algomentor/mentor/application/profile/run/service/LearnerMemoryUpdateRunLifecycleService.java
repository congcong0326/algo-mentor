package org.congcong.algomentor.mentor.application.profile.run.service;

import java.time.Clock;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 在主应用事务回滚后，以独立短事务落库 update run 失败终态。 */
public final class LearnerMemoryUpdateRunLifecycleService {

  private final LearnerMemoryUpdateRunRepository repository;
  private final TransactionTemplate requiresNewTransaction;
  private final Clock clock;

  public LearnerMemoryUpdateRunLifecycleService(
      LearnerMemoryUpdateRunRepository repository,
      TransactionTemplate transactionTemplate) {
    this(repository, transactionTemplate, Clock.systemUTC());
  }

  public LearnerMemoryUpdateRunLifecycleService(
      LearnerMemoryUpdateRunRepository repository,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.requiresNewTransaction = requiresNewTransaction(transactionTemplate);
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void markFailed(LearnerMemoryOperationBatch batch, LearnerMemoryOperationFailure.Code code) {
    markFailed(batch.userId(), batch.updateRunId(), batch.toolCallCount(), code);
  }

  public void markFailed(
      long userId,
      long updateRunId,
      int toolCallCount,
      LearnerMemoryOperationFailure.Code code) {
    if (userId <= 0 || updateRunId <= 0 || toolCallCount < 0 || code == null) {
      throw new IllegalArgumentException("learner memory failed run 参数非法。");
    }
    requiresNewTransaction.executeWithoutResult(status -> repository.findById(updateRunId)
        .filter(run -> run.userId() == userId && run.status() == LearnerMemoryRunContract.Status.RUNNING)
        .ifPresent(run -> repository.complete(
            run.id(), LearnerMemoryRunContract.Status.FAILED, 0, toolCallCount, code.name(), clock.instant())));
  }

  private static TransactionTemplate requiresNewTransaction(TransactionTemplate source) {
    TransactionTemplate template = new TransactionTemplate(
        Objects.requireNonNull(Objects.requireNonNull(source, "transactionTemplate").getTransactionManager(),
            "transactionManager"));
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }
}
