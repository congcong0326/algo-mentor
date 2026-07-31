package org.congcong.algomentor.mentor.application.profile.operation.model;

import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;

/** 同一用户、同一 RUNNING update run 的全有或全无 operation 批次。 */
public record LearnerMemoryOperationBatch(
    long userId,
    long updateRunId,
    LearnerMemorySnapshotToken expectedSnapshotToken,
    int toolCallCount,
    List<LearnerMemoryOperation> operations
) {

  public static final int MAX_OPERATIONS = 12;

  public LearnerMemoryOperationBatch {
    if (userId <= 0 || updateRunId <= 0 || expectedSnapshotToken == null || toolCallCount < 0) {
      throw new IllegalArgumentException("learner memory operation batch 固定字段非法。");
    }
    operations = operations == null ? List.of() : List.copyOf(operations);
    if (operations.size() > MAX_OPERATIONS || operations.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("learner memory operation batch 数量或元素非法。");
    }
    long targets = operations.stream().map(LearnerMemoryOperation::targetId)
        .filter(Objects::nonNull)
        .distinct()
        .count();
    if (targets != operations.stream().map(LearnerMemoryOperation::targetId)
        .filter(Objects::nonNull).count()) {
      throw new IllegalArgumentException("learner memory operation batch 不能重复目标 revision。");
    }
  }
}
