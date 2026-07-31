package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewEvent;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewQueueContracts;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 正式 Review 的唯一关键提交入口。
 *
 * <p>LLM 决策必须在调用本方法前完成；此处仅在一个 REQUIRED 事务中写入 Review、合法标签和队列消息。</p>
 */
public class PracticeCodeReviewCommitService {
  private final PracticeCodeReviewRepository repository;
  private final QueuePublisher queuePublisher;

  public PracticeCodeReviewCommitService(PracticeCodeReviewRepository repository, QueuePublisher queuePublisher) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.queuePublisher = Objects.requireNonNull(queuePublisher, "queuePublisher must not be null");
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public PracticeCodeReviewCommitResult commit(PracticeCodeReviewDraft draft) {
    PracticeCodeReviewSaveResult saved = repository.saveResult(draft);
    if (!saved.created()) {
      return new PracticeCodeReviewCommitResult(saved.review(), false, null);
    }
    QueueMessage message = queuePublisher.publish(
        LearnerMemoryCodeReviewQueueContracts.TOPIC,
        LearnerMemoryCodeReviewQueueContracts.keyForUser(saved.review().userId()),
        new LearnerMemoryCodeReviewEvent(saved.review().id()));
    return new PracticeCodeReviewCommitResult(saved.review(), true, message.messageId());
  }
}
