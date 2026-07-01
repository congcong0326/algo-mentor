package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;

public record LearningPlanExtensionDraft(
    String summary,
    List<LearningPlanPhaseDraft> newPhases,
    Map<String, Object> metadata
) {

  public LearningPlanExtensionDraft {
    summary = summary == null ? "" : summary.trim();
    newPhases = newPhases == null ? List.of() : List.copyOf(newPhases);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
