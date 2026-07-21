package org.congcong.algomentor.mentor.application.profile;

import java.time.Instant;

/** 服务端构造的待持久化画像版本，模型不能控制身份、版本或状态。 */
public record LearnerProfileEntryDraft(
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
    Instant validTo
) {

  public LearnerProfileEntryDraft {
    if (identity == null || revisionNo < 1 || status == null || contentText == null || contentText.isBlank()
        || originType == null || validFrom == null || (status == LearnerProfileEntryStatus.ACTIVE) != (validTo == null)) {
      throw new IllegalArgumentException("Invalid learner profile entry draft");
    }
  }
}
