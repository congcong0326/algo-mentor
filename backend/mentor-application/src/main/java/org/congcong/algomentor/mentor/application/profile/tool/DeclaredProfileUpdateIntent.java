package org.congcong.algomentor.mentor.application.profile.tool;

import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimOrigin;

/** 用户在当前消息中表达长期事实的意图类型。 */
public enum DeclaredProfileUpdateIntent {
  DECLARE,
  CORRECT;

  public LearnerMemoryClaimOrigin originType() {
    return this == CORRECT ? LearnerMemoryClaimOrigin.USER_CORRECTION : LearnerMemoryClaimOrigin.USER_EXPLICIT;
  }
}
