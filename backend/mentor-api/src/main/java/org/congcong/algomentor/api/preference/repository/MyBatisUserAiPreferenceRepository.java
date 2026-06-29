package org.congcong.algomentor.api.preference.repository;

import java.util.Optional;
import org.congcong.algomentor.api.preference.mapper.UserAiPreferenceMapper;
import org.congcong.algomentor.api.preference.mapper.model.UserAiPreferenceRow;
import org.congcong.algomentor.mentor.application.preference.UserAiPreference;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;

public class MyBatisUserAiPreferenceRepository implements UserAiPreferenceRepository {

  private final UserAiPreferenceMapper mapper;

  public MyBatisUserAiPreferenceRepository(UserAiPreferenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<UserAiPreference> findByUserId(long userId) {
    return Optional.ofNullable(mapper.findByUserId(userId)).map(this::toPreference);
  }

  @Override
  public UserAiPreference upsert(UserAiPreference preference) {
    return toPreference(mapper.upsert(
        preference.userId(),
        preference.coachStyle().name()));
  }

  private UserAiPreference toPreference(UserAiPreferenceRow row) {
    return new UserAiPreference(
        row.userId(),
        PracticeCoachStyle.from(row.coachStyle()),
        row.createdAt(),
        row.updatedAt());
  }
}
