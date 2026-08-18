package org.congcong.algomentor.mentor.application.profile.evidence.model;

/** 证据角色、形态和服务端计算强度的跨模块固定契约。 */
public final class LearnerMemoryEvidenceContract {

  private LearnerMemoryEvidenceContract() {
  }

  public enum ReviewRole {
    OBSERVED,
    PERSISTED,
    RESOLVED,
    REGRESSED,
    CONTRADICTS
  }

  public enum MessageRole {
    DECLARED,
    CORRECTED
  }

  public enum Pattern {
    USER_DECLARATION,
    USER_CORRECTION,
    SINGLE_REVIEW,
    SAME_PROBLEM_PERSISTENCE,
    SAME_PROBLEM_RECOVERY,
    SAME_PROBLEM_REGRESSION,
    CROSS_PROBLEM_RECOVERY,
    CROSS_PROBLEM_RECURRENCE,
    CROSS_PROBLEM_LONGITUDINAL,
    TAG_BREADTH
  }

  public enum Grade {
    LIMITED,
    SUPPORTED,
    STRONG,
    USER_AUTHORED
  }
}
