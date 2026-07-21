package org.congcong.algomentor.mentor.application.profile;

import java.util.Optional;

/** apply 结果；STALE 携带最新条目供调用方在事务外重算。 */
public record ProfileUpdateApplyResult(
    ProfileUpdateApplyStatus status,
    Optional<LearnerProfileEntry> currentEntry,
    String snapshotToken
) {
  public ProfileUpdateApplyResult {
    if (status == null || currentEntry == null || snapshotToken == null || snapshotToken.isBlank()) {
      throw new IllegalArgumentException("Invalid profile update apply result");
    }
  }
}
