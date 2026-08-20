package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentPreparedStream;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;
import org.junit.jupiter.api.Test;

class LearningPlanDraftGenerationServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-08-20T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void createsCollectingDraftSynchronouslyWithoutAgentOrDailyQuotaWhenInputIsMissing() {
    RecordingRepository repository = new RecordingRepository();
    RecordingAgentRuntime runtime = new RecordingAgentRuntime();
    LearningPlanDraftGenerationService service = new LearningPlanDraftGenerationService(
        repository,
        new LearningPlanDraftValidator(),
        runtime,
        new ObjectMapper(),
        new EmptyProblemCatalog(),
        new LearningPlanLoadService(clock),
        clock,
        new LearningPlanPersonalizationContextService(null),
        new LearningPlanCreationPolicyService(),
        (draftId, event) -> { throw new AssertionError("Redis event must not be written"); });

    LearningPlanDraftGenerationStart start = service.start(7L, incompleteBrief(), "generation-key");

    assertThat(start.newlyStarted()).isTrue();
    assertThat(start.draft().status()).isEqualTo(LearningPlanDraftStatus.COLLECTING);
    assertThat(start.draft().missingFields()).contains("intent");
    assertThat(start.draft().assistantMessage()).isEqualTo(
        "你想创建哪类学习计划？例如面试冲刺、专题突破或长期学习。");
    assertThat(start.draft().generationRequestKey()).isEqualTo("generation-key");
    assertThat(start.draft().generationRequestFingerprint()).hasSize(64);
    assertThat(runtime.prepareCalls).isZero();
    assertThat(repository.dailyCreationAttempts).isZero();
    assertThat(repository.drafts).hasSize(1);
  }

  private LearningPlanBrief incompleteBrief() {
    return new LearningPlanBrief(
        null,
        "",
        15,
        3,
        LearningPlanLevel.INTERMEDIATE,
        5,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        true,
        LearningPlanContentLocale.ZH_CN);
  }

  private static final class RecordingAgentRuntime implements AgentRuntime {

    private int prepareCalls;

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AgentPreparedStream prepareStream(AgentInvocation<?> invocation) {
      prepareCalls++;
      throw new AssertionError("Agent must not be prepared for a collecting draft");
    }
  }

  private static final class EmptyProblemCatalog implements LearningPlanProblemCatalog {

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return Optional.empty();
    }
  }

  private static final class RecordingRepository implements LearningPlanDraftRepository {

    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();
    private int dailyCreationAttempts;
    private long nextId = 100L;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      long id = draft.id() == null ? nextId++ : draft.id();
      LearningPlanDraft saved = draft.withId(id);
      drafts.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> createWithinDailyLimit(
        LearningPlanDraft draft,
        LocalDate quotaDate,
        int dailyLimit,
        Instant consumedAt
    ) {
      dailyCreationAttempts++;
      return Optional.of(save(draft));
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(draft -> draft.userId() == userId);
    }
  }
}
