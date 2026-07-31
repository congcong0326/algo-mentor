package org.congcong.algomentor.mentor.application.profile.run.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRun;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunDraft;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryUpdateRunReview;

/** 记忆更新 run、业务幂等和触发 Review 的事务内端口。 */
public interface LearnerMemoryUpdateRunRepository {

  Optional<LearnerMemoryUpdateRun> findByIdempotencyKey(String idempotencyKey);

  Optional<LearnerMemoryUpdateRun> findById(long updateRunId);

  LearnerMemoryUpdateRun create(LearnerMemoryUpdateRunDraft draft);

  void insertTriggerReviews(Collection<LearnerMemoryUpdateRunReview> reviews);

  List<LearnerMemoryUpdateRunReview> findTriggerReviews(long updateRunId);

  void bindAgentRun(long updateRunId, long agentRunId);

  void complete(
      long updateRunId,
      LearnerMemoryRunContract.Status status,
      int operationCount,
      int toolCallCount,
      String failureCode,
      Instant completedAt);
}
