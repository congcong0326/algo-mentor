package org.congcong.algomentor.mentor.application.preference;

import java.util.Optional;

public interface UserAiPreferenceRepository {

  Optional<UserAiPreference> findByUserId(long userId);

  UserAiPreference upsert(UserAiPreference preference);

  static UserAiPreferenceRepository empty() {
    return new UserAiPreferenceRepository() {
      @Override
      public Optional<UserAiPreference> findByUserId(long userId) {
        return Optional.empty();
      }

      @Override
      public UserAiPreference upsert(UserAiPreference preference) {
        return preference;
      }
    };
  }
}
