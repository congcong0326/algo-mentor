package org.congcong.algomentor.mentor.application.review.preference;

import java.util.Optional;

public interface ReviewPreferenceRepository {

  Optional<ReviewPreference> findByUserId(long userId);

  ReviewPreference upsert(ReviewPreference preference);

  static ReviewPreferenceRepository empty() {
    return new ReviewPreferenceRepository() {
      @Override
      public Optional<ReviewPreference> findByUserId(long userId) {
        return Optional.empty();
      }

      @Override
      public ReviewPreference upsert(ReviewPreference preference) {
        return preference;
      }
    };
  }
}
