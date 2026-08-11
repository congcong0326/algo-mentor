package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanAbilityTagSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanActiveProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationDataProvider;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanReviewLoadSummary;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;
import org.junit.jupiter.api.Test;

class LearningPlanDraftStreamServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();

  @Test
  void streamsWorkEventsAndDraftReadyWithValidatedProblems() {
    LearningPlanDraftStreamService service = serviceWithAgent(finalJson("two-sum", "fake-slug"));

    List<LearningPlanDraftStreamEvent> events = collect(service.stream(7L, command(), "run-1", Map.of()));

    assertThat(events).as("stream events: %s", events).extracting(LearningPlanDraftStreamEvent::eventName)
        .contains("work_start", "work_progress", "work_done", "draft_ready");
    LearningPlanDraftStreamEvent.Draft draftEvent = (LearningPlanDraftStreamEvent.Draft) events.stream()
        .filter(event -> event.eventName().equals("draft_ready"))
        .findFirst()
        .orElseThrow();
    LearningPlanDraftEvent.DraftReady ready = (LearningPlanDraftEvent.DraftReady) draftEvent.event();
    assertThat(ready.draft().status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(ready.draft().draftPlan().phases().get(0).problems())
        .extracting(problem -> problem.slug())
        .containsExactly("two-sum");
    assertThat(ready.draft().draftPlan().metadata())
        .containsEntry("contentLocale", "en-US")
        .containsOnlyKeys(
            "contentLocale",
            "personalizationEnabled",
            "dailyProblemCount",
            "trainingDaysPerWeek",
            "coveragePolicy",
            "loadSummary");
  }

  @Test
  void missingFieldsReturnsCollectingDraftWithoutAgentRun() {
    FakeAgentRuntime runtime = new FakeAgentRuntime("{}");
    LearningPlanDraftStreamService service = serviceWithRuntime(runtime);

    List<LearningPlanDraftStreamEvent> events = collect(service.stream(7L, new LearningPlanBrief(
        null,
        "",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        true,
        LearningPlanContentLocale.ZH_CN), "run-1", Map.of()));

    assertThat(events).extracting(LearningPlanDraftStreamEvent::eventName).containsExactly("draft_ready");
    LearningPlanDraftEvent.DraftReady ready = (LearningPlanDraftEvent.DraftReady)
        ((LearningPlanDraftStreamEvent.Draft) events.get(0)).event();
    assertThat(ready.draft().status()).isEqualTo(LearningPlanDraftStatus.COLLECTING);
    assertThat(ready.draft().missingFields()).contains("intent", "objective");
    assertThat(runtime.streamCalls).isZero();
  }

  @Test
  void rejectsAiDraftBeforeAgentRunWhenDailyCreationLimitIsReached() {
    FakeAgentRuntime runtime = new FakeAgentRuntime(finalJson("two-sum"));
    LearningPlanDraftStreamService service = new LearningPlanDraftStreamService(
        draftRepository,
        new LearningPlanDraftValidator(),
        runtime,
        new ObjectMapper(),
        problemCatalog,
        new LearningPlanLoadService(clock),
        clock,
        new LearningPlanPersonalizationContextService(null),
        new LearningPlanCreationPolicyService(
            ignored -> new LearningPlanCreationPolicy(30, 0, 14),
            ZoneOffset.UTC));

    assertThatThrownBy(() -> service.stream(7L, command(), "run-limited", Map.of()))
        .isInstanceOfSatisfying(org.congcong.algomentor.mentor.application.learningplan.LearningPlanException.class,
            exception -> assertThat(exception.code()).isEqualTo(
                LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE));
    assertThat(runtime.streamCalls).isZero();
  }

  @Test
  void snapshotsEnabledPersonalizationOncePerRunAndReadsItAgainForANewRun() {
    FakePersonalizationProvider provider = new FakePersonalizationProvider();
    FakeAgentRuntime runtime = new FakeAgentRuntime(finalJson("two-sum"));
    LearningPlanDraftStreamService service = serviceWithRuntime(
        runtime, new LearningPlanPersonalizationContextService(provider, null, clock));

    collect(service.stream(7L, command(), "run-1", Map.of()));

    assertThat(provider.totalCalls()).isEqualTo(4);
    LearningPlanDraftAgentInput firstInput = (LearningPlanDraftAgentInput) runtime.invocations.get(0).input();
    assertThat(firstInput.personalizationSnapshot().enabled()).isTrue();
    assertThat(firstInput.personalizationSnapshot().sourceOutcomes().values())
        .containsOnly(LearningPlanPersonalizationSourceOutcome.EMPTY);

    collect(service.stream(7L, command(), "run-2", Map.of()));

    assertThat(provider.totalCalls()).isEqualTo(8);
    assertThat(runtime.invocations).hasSize(2);
  }

  @Test
  void doesNotReadPersonalizationSourcesWhenBriefDisablesThem() {
    FakePersonalizationProvider provider = new FakePersonalizationProvider();
    FakeAgentRuntime runtime = new FakeAgentRuntime(finalJson("two-sum"));
    LearningPlanDraftStreamService service = serviceWithRuntime(
        runtime, new LearningPlanPersonalizationContextService(provider, null, clock));

    collect(service.stream(7L, command(false), "run-disabled", Map.of()));

    assertThat(provider.totalCalls()).isZero();
    LearningPlanDraftAgentInput input = (LearningPlanDraftAgentInput) runtime.invocations.get(0).input();
    assertThat(input.personalizationSnapshot().enabled()).isFalse();
    assertThat(input.personalizationSnapshot().promptText()).isEmpty();
    assertThat(input.personalizationSnapshot().sourceOutcomes().values())
        .containsOnly(LearningPlanPersonalizationSourceOutcome.DISABLED);
  }

  @Test
  void continuesDraftGenerationWhenOnePersonalizationSourceFails() {
    FakePersonalizationProvider provider = new FakePersonalizationProvider();
    provider.claimFailure = new IllegalStateException("exception-text-must-not-leak");
    provider.reviewLoad = Optional.of(new LearningPlanReviewLoadSummary(2, 1, null));
    FakeAgentRuntime runtime = new FakeAgentRuntime(finalJson("two-sum"));
    LearningPlanDraftStreamService service = serviceWithRuntime(
        runtime, new LearningPlanPersonalizationContextService(provider, null, clock));

    List<LearningPlanDraftStreamEvent> events = collect(service.stream(7L, command(), "run-error", Map.of()));

    assertThat(events).as("stream events: %s", events).extracting(LearningPlanDraftStreamEvent::eventName)
        .contains("draft_ready");
    LearningPlanDraftAgentInput input = (LearningPlanDraftAgentInput) runtime.invocations.get(0).input();
    assertThat(input.personalizationSnapshot().sourceOutcomes())
        .containsEntry(LearningPlanPersonalizationSource.ACTIVE_CLAIMS,
            LearningPlanPersonalizationSourceOutcome.ERROR)
        .containsEntry(LearningPlanPersonalizationSource.REVIEW_LOAD,
            LearningPlanPersonalizationSourceOutcome.SUCCESS);
    assertThat(input.personalizationSnapshot().promptText()).doesNotContain("exception-text-must-not-leak");
  }

  private LearningPlanDraftStreamService serviceWithAgent(String content) {
    return serviceWithRuntime(new FakeAgentRuntime(content));
  }

  private LearningPlanDraftStreamService serviceWithRuntime(AgentRuntime runtime) {
    return new LearningPlanDraftStreamService(
        draftRepository,
        new LearningPlanDraftValidator(),
        runtime,
        new ObjectMapper(),
        problemCatalog,
        new LearningPlanLoadService(clock),
        clock);
  }

  private LearningPlanDraftStreamService serviceWithRuntime(
      AgentRuntime runtime,
      LearningPlanPersonalizationContextService personalizationContextService
  ) {
    return new LearningPlanDraftStreamService(
        draftRepository,
        new LearningPlanDraftValidator(),
        runtime,
        new ObjectMapper(),
        problemCatalog,
        new LearningPlanLoadService(clock),
        clock,
        personalizationContextService);
  }

  private LearningPlanBrief command() {
    return command(true);
  }

  private LearningPlanBrief command(boolean personalizationEnabled) {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        personalizationEnabled,
        LearningPlanContentLocale.EN_US);
  }

  private String finalJson(String... slugs) {
    String problems = java.util.Arrays.stream(slugs)
        .map(slug -> """
            {
              "slug": "%s",
              "frontendId": 1,
              "title": "Two Sum",
              "titleCn": "两数之和",
              "difficulty": "EASY",
              "tags": ["Array"],
              "reason": "匹配数组训练目标。",
              "sortOrder": 1
            }
            """.formatted(slug))
        .reduce((left, right) -> left + "," + right)
        .orElse("");
    return """
        {
          "title": "四周 Java 算法面试冲刺计划",
          "summary": "围绕数组和哈希表建立高频题能力。",
          "phases": [
            {
              "phaseIndex": 1,
              "title": "数组与哈希表基础",
              "durationWeeks": 2,
              "focus": "Array",
              "problems": [%s]
            },
            {
              "phaseIndex": 2,
              "title": "二分与双指针",
              "durationWeeks": 1,
              "focus": "Binary Search",
              "problems": []
            },
            {
              "phaseIndex": 3,
              "title": "动态规划入门",
              "durationWeeks": 1,
              "focus": "Dynamic Programming",
              "problems": []
            }
          ]
        }
        """.formatted(problems);
  }

  private List<LearningPlanDraftStreamEvent> collect(Flow.Publisher<LearningPlanDraftStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    return subscriber.events;
  }

  private static class FakeAgentRuntime implements AgentRuntime {
    private final String content;
    private final List<AgentInvocation<?>> invocations = new ArrayList<>();
    private int streamCalls;

    FakeAgentRuntime(String content) {
      this.content = content;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      streamCalls++;
      invocations.add(invocation);
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        String runId = invocation.context().idempotencyKey();
        publisher.submit(new AgentStreamEvent.AgentRunStart(runId, "learning_plan", 4));
        publisher.submit(new AgentStreamEvent.AgentStepStart(runId, 1));
        publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta(content)));
        publisher.submit(new AgentStreamEvent.AgentStepEnd(runId, 1, LlmFinishReason.STOP, 0));
        publisher.submit(new AgentStreamEvent.AgentRunEnd(runId, 1, LlmFinishReason.STOP, Map.of()));
        publisher.close();
      };
    }
  }

  private static final class FakePersonalizationProvider implements LearningPlanPersonalizationDataProvider {

    private List<LearningPlanAbilityTagSummary> tags = List.of();
    private Optional<LearningPlanActiveProgressSummary> activePlan = Optional.empty();
    private Optional<LearningPlanReviewLoadSummary> reviewLoad = Optional.empty();
    private RuntimeException claimFailure;
    private int claimCalls;
    private int tagCalls;
    private int activePlanCalls;
    private int reviewLoadCalls;

    @Override
    public List<org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision>
        findActiveClaims(long userId) {
      claimCalls++;
      if (claimFailure != null) {
        throw claimFailure;
      }
      return List.of();
    }

    @Override
    public List<LearningPlanAbilityTagSummary> findAbilityTagSummaries(long userId) {
      tagCalls++;
      return tags;
    }

    @Override
    public Optional<LearningPlanActiveProgressSummary> findActivePlanProgress(long userId) {
      activePlanCalls++;
      return activePlan;
    }

    @Override
    public Optional<LearningPlanReviewLoadSummary> findReviewLoad(long userId) {
      reviewLoadCalls++;
      return reviewLoad;
    }

    private int totalCalls() {
      return claimCalls + tagCalls + activePlanCalls + reviewLoadCalls;
    }
  }

  private static class CollectingSubscriber implements Flow.Subscriber<LearningPlanDraftStreamEvent> {
    private final List<LearningPlanDraftStreamEvent> events = new ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    @Override
    public void onNext(LearningPlanDraftStreamEvent item) {
      events.add(item);
      subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    void await() {
      try {
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError(exception);
      }
    }
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {
    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      if (!"two-sum".equals(slug)) {
        return Optional.empty();
      }
      return Optional.of(new LearningPlanProblemCandidate(
          "two-sum",
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table")));
    }
  }

  private static class InMemoryDraftRepository implements LearningPlanDraftRepository {
    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();
    private long sequence = 100;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      long id = draft.id() == null ? sequence++ : draft.id();
      LearningPlanDraft saved = draft.withId(id);
      drafts.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(draft -> draft.userId() == userId);
    }
  }
}
