package org.congcong.algomentor.mentor.application.profile;

import java.util.Optional;

/** 事务外模型调用使用的当前画像快照与陈旧检测令牌。 */
public record LearnerProfileSnapshot(
    LearnerProfileIdentity identity,
    Optional<LearnerProfileEntry> currentEntry,
    String snapshotToken
) {
  public static final String ABSENT_TOKEN = "ABSENT";

  public LearnerProfileSnapshot {
    if (identity == null || currentEntry == null || snapshotToken == null || snapshotToken.isBlank()) {
      throw new IllegalArgumentException("Invalid learner profile snapshot");
    }
  }

  public static LearnerProfileSnapshot from(LearnerProfileIdentity identity, LearnerProfileEntry entry) {
    return new LearnerProfileSnapshot(
        identity, Optional.ofNullable(entry), entry == null ? ABSENT_TOKEN : entry.id() + ":" + entry.revisionNo());
  }
}
