package org.congcong.algomentor.api.preference.model;

import org.congcong.algomentor.mentor.application.preference.UserAiPreference;

public final class UserAiPreferenceResponseMapper {

  private UserAiPreferenceResponseMapper() {
  }

  public static UserAiPreferenceResponse toResponse(UserAiPreference preference) {
    return new UserAiPreferenceResponse(
        preference.coachStyle().name(),
        preference.coachStyle().label(),
        preference.updatedAt());
  }
}
