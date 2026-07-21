package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;

/** 单个 Agent run 在 loop 前固定的画像读取结果，永不包含历史或 suppressed 条目。 */
public record LearnerProfileRecallSnapshot(
    List<LearnerProfileEntry> declaredFacts,
    List<LearnerProfileEntry> generalObservations,
    List<TagAssessment> currentProblemTagAssessments
) {

  public LearnerProfileRecallSnapshot {
    declaredFacts = declaredFacts == null ? List.of() : List.copyOf(declaredFacts);
    generalObservations = generalObservations == null ? List.of() : List.copyOf(generalObservations);
    currentProblemTagAssessments = currentProblemTagAssessments == null
        ? List.of()
        : List.copyOf(currentProblemTagAssessments);
  }

  public static LearnerProfileRecallSnapshot empty() {
    return new LearnerProfileRecallSnapshot(List.of(), List.of(), List.of());
  }

  public boolean isEmpty() {
    return declaredFacts.isEmpty() && generalObservations.isEmpty() && currentProblemTagAssessments.isEmpty();
  }

  public int entryCount() {
    return declaredFacts.size() + generalObservations.size() + currentProblemTagAssessments.size();
  }

  /** 标签名称来自当前题目的受信 assignment，不允许由画像正文或模型补充。 */
  public record TagAssessment(TrustedProblemTag tag, LearnerProfileEntry entry) {
    public TagAssessment {
      if (tag == null || entry == null || entry.identity().tagId() == null
          || entry.identity().tagId() != tag.tagId()) {
        throw new IllegalArgumentException("Invalid current problem learner profile tag assessment");
      }
    }
  }
}
