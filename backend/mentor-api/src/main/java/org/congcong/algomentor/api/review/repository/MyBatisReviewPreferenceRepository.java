package org.congcong.algomentor.api.review.repository;

import java.util.Optional;
import org.congcong.algomentor.api.review.mapper.ReviewPreferenceMapper;
import org.congcong.algomentor.api.review.mapper.model.ReviewPreferenceRow;
import org.congcong.algomentor.mentor.application.review.ReviewPreference;
import org.congcong.algomentor.mentor.application.review.ReviewPreferenceRepository;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisReviewPreferenceRepository implements ReviewPreferenceRepository {

  private final ReviewPreferenceMapper mapper;

  public MyBatisReviewPreferenceRepository(ReviewPreferenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ReviewPreference> findByUserId(long userId) {
    return Optional.ofNullable(mapper.findByUserId(userId)).map(this::toPreference);
  }

  @Override
  @Transactional
  public ReviewPreference upsert(ReviewPreference preference) {
    return toPreference(mapper.upsert(
        preference.userId(),
        preference.desiredRetention(),
        preference.dailyNewLimit(),
        preference.dailyLearningLimit(),
        preference.dailyReviewLimit(),
        preference.aiSuggestionEnabled(),
        preference.maximumIntervalDays(),
        preference.enableFuzzing(),
        preference.createdAt(),
        preference.updatedAt()));
  }

  private ReviewPreference toPreference(ReviewPreferenceRow row) {
    return new ReviewPreference(
        row.userId(),
        row.desiredRetention(),
        row.dailyNewLimit(),
        row.dailyLearningLimit(),
        row.dailyReviewLimit(),
        row.aiSuggestionEnabled(),
        row.maximumIntervalDays(),
        row.enableFuzzing(),
        row.createdAt(),
        row.updatedAt());
  }
}
