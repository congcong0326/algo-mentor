package org.congcong.algomentor.mentor.application.profile.run.model;

import java.time.Instant;

/** 一次声明或 Code Review 批次触发的记忆更新尝试。 */
public record LearnerMemoryUpdateRun(
    long id,
    long userId,
    LearnerMemoryRunContract.Trigger trigger,
    LearnerMemoryRunContract.Status status,
    String idempotencyKey,
    Long agentRunId,
    String promptVersion,
    String schemaVersion,
    int inputCount,
    int operationCount,
    int toolCallCount,
    String failureCode,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt
) {

  public LearnerMemoryUpdateRun {
    requirePositive(id, "update run id");
    requirePositive(userId, "user id");
    if (trigger == null || status == null || idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("update run 固定字段不能为空。");
    }
    if (agentRunId != null) {
      requirePositive(agentRunId, "agent run id");
    }
    if (inputCount < 0 || operationCount < 0 || toolCallCount < 0) {
      throw new IllegalArgumentException("update run 计数不能为负数。");
    }
    if (startedAt == null || createdAt == null) {
      throw new IllegalArgumentException("update run 时间不能为空。");
    }
    if (status.isTerminal() != (completedAt != null)) {
      throw new IllegalArgumentException("update run 状态与完成时间不一致。");
    }
    if (completedAt != null && completedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("completedAt 不能早于 startedAt。");
    }
    idempotencyKey = idempotencyKey.trim();
    promptVersion = normalizeNullable(promptVersion);
    schemaVersion = normalizeNullable(schemaVersion);
    failureCode = normalizeNullable(failureCode);
  }

  private static String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " 必须为正数。");
    }
  }
}
