package org.congcong.algomentor.mentor.application.profile;

/** 画像版本状态；同一业务身份仅允许一个 ACTIVE 版本。 */
public enum LearnerProfileEntryStatus {
  ACTIVE,
  SUPERSEDED,
  SUPPRESSED
}
