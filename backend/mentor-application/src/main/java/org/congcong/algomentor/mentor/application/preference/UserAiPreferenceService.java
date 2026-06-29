package org.congcong.algomentor.mentor.application.preference;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;

public class UserAiPreferenceService {

  private final UserAiPreferenceRepository repository;
  private final Clock clock;

  public UserAiPreferenceService(UserAiPreferenceRepository repository) {
    this(repository, Clock.systemUTC());
  }

  public UserAiPreferenceService(UserAiPreferenceRepository repository, Clock clock) {
    this.repository = repository == null ? UserAiPreferenceRepository.empty() : repository;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public UserAiPreference get(long userId) {
    return repository.findByUserId(userId).orElseGet(() -> defaultPreference(userId));
  }

  public UserAiPreference update(long userId, UserAiPreferenceUpdate update) {
    UserAiPreference current = get(userId);
    PracticeCoachStyle coachStyle = update == null || update.coachStyle() == null
        ? current.coachStyle()
        : update.coachStyle();
    Instant now = Instant.now(clock);
    return repository.upsert(new UserAiPreference(
        userId,
        coachStyle,
        current.createdAt() == null ? now : current.createdAt(),
        now));
  }

  private UserAiPreference defaultPreference(long userId) {
    Instant now = Instant.now(clock);
    return new UserAiPreference(
        userId,
        PracticeCoachStyle.defaultStyle(),
        now,
        now);
  }
}
