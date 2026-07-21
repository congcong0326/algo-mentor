package org.congcong.algomentor.mentor.application.profile;

/** 同一条画像版本链的业务身份。 */
public record LearnerProfileIdentity(
    long userId,
    LearnerProfileEntryKind entryKind,
    LearnerProfileDimension dimension,
    Long tagId
) {

  public LearnerProfileIdentity {
    if (userId < 1 || !LearnerProfileContract.isValidScope(entryKind, dimension, tagId)) {
      throw new IllegalArgumentException("Invalid learner profile identity");
    }
  }

  public static LearnerProfileIdentity dimension(
      long userId,
      LearnerProfileEntryKind entryKind,
      LearnerProfileDimension dimension) {
    return new LearnerProfileIdentity(userId, entryKind, dimension, null);
  }

  public static LearnerProfileIdentity tagAssessment(long userId, long tagId) {
    return new LearnerProfileIdentity(
        userId,
        LearnerProfileEntryKind.TAG_ASSESSMENT,
        LearnerProfileDimension.TAG_MASTERY,
        tagId);
  }
}
