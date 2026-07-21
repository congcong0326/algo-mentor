package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyResult;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyStatus;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateCommand;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateDecision;
import org.junit.jupiter.api.Test;

class LearnerProfileConcurrencyIT extends PostgresIntegrationTestSupport {

  @Test
  void serializesAbsentIdentityUpdatesWithUserRowLockAndReturnsStaleToOneCaller() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MyBatisLearnerProfileRepository repository = new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
    LearnerProfileUpdateService service = new LearnerProfileUpdateService(
        repository, new LearnerProfileContentPolicy(200), transactionTemplate());
    LearnerProfileIdentity identity = LearnerProfileIdentity.dimension(
        userId, LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.LEARNER_BACKGROUND);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ProfileUpdateApplyResult> first = executor.submit(applyAfterBarrier(
          service, command(identity, "first"), ready, start));
      Future<ProfileUpdateApplyResult> second = executor.submit(applyAfterBarrier(
          service, command(identity, "second"), ready, start));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<ProfileUpdateApplyResult> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

      assertThat(results).extracting(ProfileUpdateApplyResult::status)
          .containsExactlyInAnyOrder(ProfileUpdateApplyStatus.APPLIED, ProfileUpdateApplyStatus.STALE);
      assertThat(repository.findHistory(identity)).hasSize(1);
      assertThat(repository.findCurrent(identity)).isPresent();
      assertThat(queryLong("SELECT COUNT(*) FROM learner_profile_entry WHERE status = 'ACTIVE'")).isEqualTo(1L);
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<ProfileUpdateApplyResult> applyAfterBarrier(
      LearnerProfileUpdateService service,
      ProfileUpdateCommand command,
      CountDownLatch ready,
      CountDownLatch start) {
    return () -> {
      ready.countDown();
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("concurrent profile update did not start");
      }
      return service.apply(command);
    };
  }

  private ProfileUpdateCommand command(LearnerProfileIdentity identity, String content) {
    return new ProfileUpdateCommand(
        identity,
        new ProfileUpdateDecision(ProfileUpdateAction.REPLACE, content, "test"),
        LearnerProfileSnapshot.ABSENT_TOKEN,
        LearnerProfileOriginType.USER_EXPLICIT,
        "test", "test", "v1");
  }
}
