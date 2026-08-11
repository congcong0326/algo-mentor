package org.congcong.algomentor.api.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupResult;
import org.congcong.algomentor.mentor.application.learningplan.cleanup.LearningPlanDraftCleanupService;
import org.junit.jupiter.api.Test;

class LearningPlanCreationGovernanceIT extends PostgresIntegrationTestSupport {

  private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");
  private static final LocalDate QUOTA_DATE = LocalDate.parse("2026-08-11");

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void dailyDraftQuotaIsAtomicAndRollsBackWithDraftCreation() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MyBatisLearningPlanRepository repository = repository();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Optional<LearningPlanDraft>> first = executor.submit(() -> {
        ready.countDown();
        start.await();
        return Objects.requireNonNull(transactionTemplate().execute(status ->
            repository.createWithinDailyLimit(draft(userId, NOW.plusSeconds(86_400)), QUOTA_DATE, 1, NOW)));
      });
      Future<Optional<LearningPlanDraft>> second = executor.submit(() -> {
        ready.countDown();
        start.await();
        return Objects.requireNonNull(transactionTemplate().execute(status ->
            repository.createWithinDailyLimit(draft(userId, NOW.plusSeconds(86_400)), QUOTA_DATE, 1, NOW)));
      });

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .filteredOn(Optional::isPresent)
          .hasSize(1);
    } finally {
      executor.shutdownNow();
    }

    assertThat(queryLong("SELECT draft_count FROM learning_plan_daily_draft_usage WHERE user_id = ?", userId))
        .isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM learning_plan_draft WHERE user_id = ?", userId))
        .isEqualTo(1L);

    LocalDate rollbackDate = QUOTA_DATE.plusDays(1);
    assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
      repository.createWithinDailyLimit(
          draft(userId, NOW.plusSeconds(172_800)), rollbackDate, 1, NOW.plusSeconds(86_400));
      throw new IllegalStateException("force rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_daily_draft_usage WHERE user_id = ? AND quota_date = ?",
        userId,
        rollbackDate)).isZero();
  }

  @Test
  void concurrentPlanCreationCannotExceedThePerUserLimit() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MyBatisLearningPlanRepository repository = repository();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Optional<LearningPlan>> first = executor.submit(() -> {
        ready.countDown();
        start.await();
        return Objects.requireNonNull(transactionTemplate().execute(status ->
            repository.createIfBelowLimit(plan(userId, "Plan A"), 1)));
      });
      Future<Optional<LearningPlan>> second = executor.submit(() -> {
        ready.countDown();
        start.await();
        return Objects.requireNonNull(transactionTemplate().execute(status ->
            repository.createIfBelowLimit(plan(userId, "Plan B"), 1)));
      });

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .filteredOn(Optional::isPresent)
          .hasSize(1);
    } finally {
      executor.shutdownNow();
    }

    assertThat(queryLong("SELECT COUNT(*) FROM learning_plan WHERE user_id = ?", userId)).isEqualTo(1L);
  }

  @Test
  void cleanupDeletesExpiredDraftGraphAndOldUsageOnly() throws Exception {
    migrateLatest();
    long userId = insertUser();
    MyBatisLearningPlanRepository repository = repository();
    LearningPlanDraft expired = repository.save(draft(userId, NOW.minusSeconds(1)));
    LearningPlanDraft retained = repository.save(draft(userId, NOW.plusSeconds(1)));
    long proposalGroupId = queryLong(
        """
        INSERT INTO learning_plan_proposal_group (
          user_id, proposal_type, target_type, target_id, status, initial_instruction
        ) VALUES (?, 'DRAFT_REVISION', 'DRAFT', ?, 'ACTIVE', 'revise')
        RETURNING id
        """,
        userId,
        expired.id());
    execute(
        """
        INSERT INTO learning_plan_draft_revision (
          proposal_group_id, draft_id, user_id, revision_no, status, instruction
        ) VALUES (?, ?, ?, 1, 'READY', 'revise')
        """,
        proposalGroupId,
        expired.id(),
        userId);
    execute(
        """
        INSERT INTO learning_plan_daily_draft_usage (
          user_id, quota_date, draft_count, applied_limit
        ) VALUES (?, ?, 1, 5), (?, ?, 1, 5)
        """,
        userId,
        LocalDate.parse("2026-07-11"),
        userId,
        QUOTA_DATE);

    LearningPlanDraftCleanupResult result = new LearningPlanDraftCleanupService(
        repository,
        Clock.fixed(NOW, ZoneOffset.UTC),
        ZoneOffset.UTC).cleanupOnce(100, 30);

    assertThat(result.deletedDrafts()).isEqualTo(1);
    assertThat(result.deletedDailyUsageRows()).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM learning_plan_draft WHERE id = ?", expired.id())).isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM learning_plan_draft WHERE id = ?", retained.id())).isEqualTo(1L);
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_proposal_group WHERE id = ?", proposalGroupId)).isZero();
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_draft_revision WHERE proposal_group_id = ?", proposalGroupId)).isZero();
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_daily_draft_usage WHERE user_id = ?", userId)).isEqualTo(1L);
  }

  private MyBatisLearningPlanRepository repository() throws Exception {
    LearningPlanMapper mapper = sqlSessionTemplate("mapper/learningplan/LearningPlanMapper.xml")
        .getMapper(LearningPlanMapper.class);
    return new MyBatisLearningPlanRepository(mapper, objectMapper);
  }

  private LearningPlanDraft draft(long userId, Instant expiresAt) {
    return new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.GENERATED,
        brief(),
        List.of(),
        List.of(),
        "ready",
        snapshot("Draft"),
        null,
        expiresAt,
        NOW,
        NOW);
  }

  private LearningPlan plan(long userId, String title) {
    return new LearningPlan(
        null,
        userId,
        LearningPlanStatus.ACTIVE,
        snapshot(title),
        NOW,
        NOW);
  }

  private LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "Prepare for interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(30, 60, 10),
        List.of("array"),
        null,
        true,
        LearningPlanContentLocale.EN_US);
  }

  private LearningPlanDraftPlan snapshot(String title) {
    return new LearningPlanDraftPlan(
        title,
        "Summary",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "Prepare for interviews",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(30, 60, 10),
        List.of("array"),
        null,
        List.of(),
        Map.of());
  }
}
