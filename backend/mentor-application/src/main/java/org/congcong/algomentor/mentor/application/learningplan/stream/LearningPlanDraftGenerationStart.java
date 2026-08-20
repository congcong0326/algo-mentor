package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;

/** 首次草案创建控制面返回的领域结果。 */
public record LearningPlanDraftGenerationStart(LearningPlanDraft draft, boolean newlyStarted) {

  public LearningPlanDraftGenerationStart {
    draft = Objects.requireNonNull(draft, "draft must not be null");
  }
}
