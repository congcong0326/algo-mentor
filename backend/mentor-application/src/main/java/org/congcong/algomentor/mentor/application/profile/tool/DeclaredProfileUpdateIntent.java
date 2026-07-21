package org.congcong.algomentor.mentor.application.profile.tool;

import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;

/** 用户在当前消息中表达长期事实的意图类型。 */
public enum DeclaredProfileUpdateIntent {
  DECLARE,
  CORRECT;

  public LearnerProfileOriginType originType() {
    return this == CORRECT ? LearnerProfileOriginType.USER_CORRECTION : LearnerProfileOriginType.USER_EXPLICIT;
  }
}
