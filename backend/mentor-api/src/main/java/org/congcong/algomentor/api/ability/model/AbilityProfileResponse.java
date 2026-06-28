package org.congcong.algomentor.api.ability.model;

import java.util.List;

public record AbilityProfileResponse(
    List<AbilityTagScoreResponse> tags,
    AbilityProfileScopeResponse scope
) {

  public AbilityProfileResponse {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
