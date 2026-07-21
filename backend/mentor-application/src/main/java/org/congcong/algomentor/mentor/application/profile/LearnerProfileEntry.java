package org.congcong.algomentor.mentor.application.profile;

import java.time.Instant;

/** 已持久化的学习者画像版本。 */
public record LearnerProfileEntry(
    long id,
    LearnerProfileIdentity identity,
    int revisionNo,
    LearnerProfileEntryStatus status,
    String contentText,
    Long supersedesEntryId,
    LearnerProfileOriginType originType,
    String modelProvider,
    String modelName,
    String promptVersion,
    Instant validFrom,
    Instant validTo,
    Instant createdAt,
    Instant updatedAt
) {

  public LearnerProfileEntry {
    if (id < 1 || revisionNo < 1 || status == null || contentText == null || contentText.isBlank()
        || originType == null || validFrom == null || createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("Invalid learner profile entry");
    }
    if ((status == LearnerProfileEntryStatus.ACTIVE) != (validTo == null)) {
      throw new IllegalArgumentException("Learner profile status and validity do not match");
    }
  }
}
