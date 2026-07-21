package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;

/** 某个受信业务场景可读取的画像范围；disabled 表示不查询也不注入。 */
public record LearnerProfilePolicy(
    boolean enabled,
    List<LearnerProfileDimension> declaredDimensions,
    List<LearnerProfileDimension> generalDimensions,
    boolean includeCurrentProblemTags,
    int maxTokenBudget
) {

  public LearnerProfilePolicy {
    if (maxTokenBudget < 0) {
      throw new IllegalArgumentException("Learner profile recall token budget must not be negative");
    }
    declaredDimensions = declaredDimensions == null ? List.of() : List.copyOf(declaredDimensions);
    generalDimensions = generalDimensions == null ? List.of() : List.copyOf(generalDimensions);
    if (declaredDimensions.stream().anyMatch(java.util.Objects::isNull)
        || generalDimensions.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Learner profile recall dimensions must not contain null");
    }
  }

  public static LearnerProfilePolicy disabled() {
    return new LearnerProfilePolicy(false, List.of(), List.of(), false, 0);
  }
}
