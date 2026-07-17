package org.congcong.algomentor.api.feedback.service;

import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** 反馈的多表写入统一通过此事务执行器完成。 */
public class FeedbackMutationExecutor {
  private final TransactionTemplate transactionTemplate;

  public FeedbackMutationExecutor(TransactionTemplate transactionTemplate) {
    this.transactionTemplate = transactionTemplate;
  }

  public <T> T execute(Supplier<T> work) {
    T result = transactionTemplate.execute(status -> work.get());
    if (result == null) {
      throw new IllegalStateException("Feedback transaction returned no result.");
    }
    return result;
  }
}
