package org.congcong.algomentor.api.profile.model;

import java.time.Instant;
import java.util.List;

public record LearnerProfileResponse(
    List<LearnerProfileEntryResponse> declaredFacts,
    List<LearnerProfileEntryResponse> generalObservations,
    List<LearnerProfileEntryResponse> tagAssessments,
    Instant updatedAt
) {

  public LearnerProfileResponse {
    declaredFacts = declaredFacts == null ? List.of() : List.copyOf(declaredFacts);
    generalObservations = generalObservations == null ? List.of() : List.copyOf(generalObservations);
    tagAssessments = tagAssessments == null ? List.of() : List.copyOf(tagAssessments);
  }
}
