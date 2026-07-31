package org.congcong.algomentor.mentor.application.profile.run.model;

import java.time.Instant;

/** 创建 RUNNING 更新 run 时尚未分配数据库主键的草稿。 */
public record LearnerMemoryUpdateRunDraft(
    long userId,
    LearnerMemoryRunContract.Trigger trigger,
    String idempotencyKey,
    String promptVersion,
    String schemaVersion,
    int inputCount,
    Instant startedAt
) {

  public LearnerMemoryUpdateRunDraft {
    if (userId <= 0 || trigger == null || idempotencyKey == null || idempotencyKey.isBlank()
        || inputCount < 0 || startedAt == null) {
      throw new IllegalArgumentException("update run draft 字段非法。");
    }
    idempotencyKey = idempotencyKey.trim();
    promptVersion = normalizeNullable(promptVersion);
    schemaVersion = normalizeNullable(schemaVersion);
  }

  private static String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
