package org.congcong.algomentor.mentor.application.profile.run.model;

/** 记忆更新 run 的触发来源与终态契约。 */
public final class LearnerMemoryRunContract {

  private LearnerMemoryRunContract() {
  }

  public enum Trigger {
    DECLARED_FACT,
    CODE_REVIEW_BATCH
  }

  public enum Status {
    RUNNING,
    SUCCEEDED,
    NO_CHANGE,
    FAILED;

    public boolean isTerminal() {
      return this != RUNNING;
    }
  }
}
