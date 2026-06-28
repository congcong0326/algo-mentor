package org.congcong.algomentor.mentor.application.preference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeCoachStyle;
import org.congcong.algomentor.mentor.application.practice.PracticeResponseLanguage;
import org.junit.jupiter.api.Test;

class UserAiPreferenceServiceTest {

  @Test
  void returnsDefaultPreferenceWhenUserHasNoSavedPreference() {
    InMemoryRepository repository = new InMemoryRepository();
    UserAiPreferenceService service = new UserAiPreferenceService(repository);

    UserAiPreference preference = service.get(42L);

    assertThat(preference.userId()).isEqualTo(42L);
    assertThat(preference.coachStyle()).isEqualTo(PracticeCoachStyle.SOCRATIC_GUIDE);
    assertThat(preference.responseLanguage()).isEqualTo(PracticeResponseLanguage.ZH_CN);
    assertThat(repository.saved).isEmpty();
  }

  @Test
  void savesSupportedCoachStyleAndResponseLanguage() {
    InMemoryRepository repository = new InMemoryRepository();
    UserAiPreferenceService service = new UserAiPreferenceService(repository);

    UserAiPreference preference = service.update(
        42L,
        new UserAiPreferenceUpdate(PracticeCoachStyle.INTERVIEWER, PracticeResponseLanguage.EN_US));

    assertThat(preference.coachStyle()).isEqualTo(PracticeCoachStyle.INTERVIEWER);
    assertThat(preference.responseLanguage()).isEqualTo(PracticeResponseLanguage.EN_US);
    assertThat(repository.saved.get(42L).coachStyle()).isEqualTo(PracticeCoachStyle.INTERVIEWER);
  }

  private static final class InMemoryRepository implements UserAiPreferenceRepository {

    private final Map<Long, UserAiPreference> saved = new HashMap<>();

    @Override
    public Optional<UserAiPreference> findByUserId(long userId) {
      return Optional.ofNullable(saved.get(userId));
    }

    @Override
    public UserAiPreference upsert(UserAiPreference preference) {
      UserAiPreference savedPreference = new UserAiPreference(
          preference.userId(),
          preference.coachStyle(),
          preference.responseLanguage(),
          Instant.parse("2026-06-28T00:00:00Z"),
          Instant.parse("2026-06-28T00:00:00Z"));
      saved.put(preference.userId(), savedPreference);
      return savedPreference;
    }
  }
}
