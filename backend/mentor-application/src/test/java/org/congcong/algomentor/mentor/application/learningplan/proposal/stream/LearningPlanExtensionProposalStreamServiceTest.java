package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionValidator;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.springframework.transaction.support.TransactionOperations;

class LearningPlanExtensionProposalStreamServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
  private final List<String> locks = new ArrayList<>();
  private final InMemoryLearningPlanRepository learningPlanRepository = new InMemoryLearningPlanRepository(locks);
  private final InMemoryProposalRepository proposalRepository = new InMemoryProposalRepository(locks);
  private final InMemoryPracticeSessionRepository practiceSessionRepository = new InMemoryPracticeSessionRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();

  @org.junit.jupiter.api.Test
  void firstExtensionGenerationCreatesPlanExtensionGroupAndReadyRevision() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    practiceSessionRepository.progress.add(progress(plan));
    CapturingAgentLoopRunner runner = new CapturingAgentLoopRunner(extensionJson("补充图论训练", "graph-valid-tree"));
    LearningPlanExtensionProposalStreamService service = serviceWithAgent(runner);

    List<LearningPlanProposalStreamEvent> events = collect(service.streamFirstRevision(
        plan.userId(),
        plan.id(),
        "追加图论阶段",
        "run-extension-1",
        Map.of("source", "test")));

    LearningPlanProposalStreamEvent finalEvent = events.get(events.size() - 1);
    assertThat(finalEvent.eventName()).isEqualTo("plan_extension_ready");
    LearningPlanProposalEvent.PlanExtensionReady ready =
        (LearningPlanProposalEvent.PlanExtensionReady) ((LearningPlanProposalStreamEvent.Proposal) finalEvent).event();
    LearningPlanExtensionResult result = ready.result();
    assertThat(result.planId()).isEqualTo(plan.id());
    assertThat(result.revisionNo()).isEqualTo(1);
    assertThat(result.status()).isEqualTo(LearningPlanProposalRevisionStatus.READY);
    assertThat(result.summary()).isEqualTo("补充图论训练");
    assertThat(result.extensionDraft().newPhases()).hasSize(1);
    assertThat(result.supersededProposalIds()).isEmpty();
    assertThat(proposalRepository.groups.get(result.proposalGroupId()).proposalType())
        .isEqualTo(LearningPlanProposalType.PLAN_EXTENSION);
    assertThat(proposalRepository.groups.get(result.proposalGroupId()).latestProposalId()).isEqualTo(result.proposalId());
    assertThat(proposalRepository.extensionRevisions.get(result.proposalId()).baseMaxPhaseIndex()).isEqualTo(1);
    assertThat(proposalRepository.extensionRevisions.get(result.proposalId()).progressSnapshot())
        .containsEntry("total", 1)
        .containsEntry("completed", 1L);
    assertThat(runner.request.get().executionOptions().structuredOutput().strategy())
        .isEqualTo(StructuredOutputStrategy.PROVIDER_NATIVE);
    assertThat(runner.request.get().executionOptions().structuredOutput().schemaName())
        .isEqualTo(LearningPlanStreamConstants.EXTENSION_SCHEMA_NAME);
    assertThat(lockOrder()).containsExactly("plan:12", "plan:12", "group:" + result.proposalGroupId());
  }

  @org.junit.jupiter.api.Test
  void nextRevisionStoresPreviousExtensionJsonAndSupersedesOldReadyRevision() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    LearningPlanProposalGroup group = proposalRepository.saveGroup(activeExtensionGroup(plan.id(), "追加图论阶段"));
    LearningPlanExtensionDraft previousExtension = extensionDraft("旧扩展", "graph-valid-tree");
    LearningPlanExtensionRevision previous = proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        group.id(),
        plan.id(),
        plan.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        "追加图论阶段",
        plan.plan(),
        Map.of(),
        1,
        null,
        null,
        null,
        null,
        null,
        clock.instant(),
        clock.instant())).withReady(null, previousExtension, clock.instant());
    previous = proposalRepository.saveExtensionRevision(previous);
    proposalRepository.saveGroup(group.withLatestProposalId(previous.id(), clock.instant()));
    LearningPlanExtensionProposalStreamService service =
        serviceWithAgent(extensionJson("补充更多图论训练", "number-of-islands"));

    List<LearningPlanProposalStreamEvent> events = collect(service.streamNextRevision(
        plan.userId(),
        plan.id(),
        group.id(),
        "把扩展阶段改成岛屿问题",
        "run-extension-2",
        Map.of()));

    LearningPlanProposalEvent.PlanExtensionReady ready =
        (LearningPlanProposalEvent.PlanExtensionReady) ((LearningPlanProposalStreamEvent.Proposal) events.get(events.size() - 1)).event();
    LearningPlanExtensionResult result = ready.result();
    LearningPlanExtensionRevision saved = proposalRepository.extensionRevisions.get(result.proposalId());
    assertThat(result.revisionNo()).isEqualTo(2);
    assertThat(result.supersededProposalIds()).containsExactly(previous.id());
    assertThat(saved.previousExtension()).isEqualTo(previousExtension);
    assertThat(proposalRepository.extensionRevisions.get(previous.id()).status())
        .isEqualTo(LearningPlanProposalRevisionStatus.SUPERSEDED);
    assertThat(proposalRepository.groups.get(group.id()).latestProposalId()).isEqualTo(result.proposalId());
  }

  @org.junit.jupiter.api.Test
  void duplicateSlugStructuredOutputStoresFailedAndEmitsPlanExtensionError() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    LearningPlanExtensionProposalStreamService service = serviceWithAgent(extensionJson("重复已有题", "two-sum"));

    List<LearningPlanProposalStreamEvent> events = collect(service.streamFirstRevision(
        plan.userId(),
        plan.id(),
        "追加阶段",
        "run-extension-3",
        Map.of()));

    assertThat(events).extracting(LearningPlanProposalStreamEvent::eventName).contains("plan_extension_error");
    LearningPlanExtensionRevision failed = proposalRepository.extensionRevisions.values().stream()
        .filter(revision -> revision.planId() == plan.id())
        .findFirst()
        .orElseThrow();
    assertThat(failed.status()).isEqualTo(LearningPlanProposalRevisionStatus.FAILED);
    assertThat(failed.errorCode()).isEqualTo("LEARNING_PLAN_EXTENSION_INVALID");
    assertThat(proposalRepository.groups.get(failed.proposalGroupId()).latestProposalId()).isNull();
  }

  @org.junit.jupiter.api.Test
  void staleCompletionDoesNotOverwriteNewerReadyRevisionOrGroupLatestProposal() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    LearningPlanProposalGroup group = proposalRepository.saveGroup(activeExtensionGroup(plan.id(), "旧扩展请求"));
    LearningPlanExtensionRevision ready = proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        group.id(),
        plan.id(),
        plan.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        "初始扩展请求",
        plan.plan(),
        Map.of(),
        1,
        null,
        null,
        null,
        null,
        null,
        clock.instant(),
        clock.instant())).withReady(null, extensionDraft("初始扩展", "graph-valid-tree"), clock.instant());
    ready = proposalRepository.saveExtensionRevision(ready);
    proposalRepository.saveGroup(group.withLatestProposalId(ready.id(), clock.instant()));
    ManualAgentLoopRunner olderRunner = new ManualAgentLoopRunner();
    LearningPlanExtensionProposalStreamService olderService = serviceWithAgent(olderRunner);
    List<LearningPlanProposalStreamEvent> olderEvents = collectAsync(olderService.streamNextRevision(
        plan.userId(),
        plan.id(),
        group.id(),
        "旧扩展请求",
        "run-old-extension",
        Map.of()));
    waitUntilRevisionCount(2);
    LearningPlanExtensionRevision newer = proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        group.id(),
        plan.id(),
        plan.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        "新扩展请求",
        plan.plan(),
        Map.of(),
        1,
        null,
        null,
        null,
        null,
        null,
        clock.instant(),
        clock.instant())).withReady(null, extensionDraft("新扩展", "graph-valid-tree"), clock.instant());
    newer = proposalRepository.saveExtensionRevision(newer);
    proposalRepository.saveGroup(group.withLatestProposalId(newer.id(), clock.instant()));

    olderRunner.complete(extensionJson("旧扩展", "number-of-islands"));
    waitUntilDone(olderEvents, 1);

    assertThat(olderEvents.get(olderEvents.size() - 1).eventName()).isEqualTo("plan_extension_error");
    LearningPlanExtensionRevision older = proposalRepository.extensionRevisions.values().stream()
        .filter(revision -> revision.instruction().equals("旧扩展请求"))
        .findFirst()
        .orElseThrow();
    assertThat(older.status()).isEqualTo(LearningPlanProposalRevisionStatus.FAILED);
    assertThat(older.errorCode()).isEqualTo("LEARNING_PLAN_EXTENSION_REVISION_SUPERSEDED");
    assertThat(proposalRepository.extensionRevisions.get(newer.id()).status()).isEqualTo(LearningPlanProposalRevisionStatus.READY);
    assertThat(proposalRepository.groups.get(group.id()).latestProposalId()).isEqualTo(newer.id());
  }

  @org.junit.jupiter.api.Test
  void streamDoesNotCreateRevisionBeforeSubscription() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    LearningPlanExtensionProposalStreamService service = serviceWithAgent(extensionJson("补充图论训练", "graph-valid-tree"));

    service.streamFirstRevision(plan.userId(), plan.id(), "追加图论阶段", "run-no-subscription", Map.of());

    assertThat(proposalRepository.extensionRevisions).isEmpty();
    assertThat(proposalRepository.groups).isEmpty();
  }

  @org.junit.jupiter.api.Test
  void rejectsDuplicateSubscriptionWithoutStartingSecondRevision() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    ManualAgentLoopRunner runner = new ManualAgentLoopRunner();
    LearningPlanExtensionProposalStreamService service = serviceWithAgent(runner);
    Flow.Publisher<LearningPlanProposalStreamEvent> publisher = service.streamFirstRevision(
        plan.userId(),
        plan.id(),
        "追加图论阶段",
        "run-duplicate",
        Map.of());
    CollectingSubscriber first = new CollectingSubscriber();
    CollectingSubscriber second = new CollectingSubscriber();

    publisher.subscribe(first);
    waitUntilRevisionCount(1);
    publisher.subscribe(second);
    second.await();
    runner.complete(extensionJson("补充图论训练", "graph-valid-tree"));
    first.await();

    assertThat(first.events.get(first.events.size() - 1).eventName()).isEqualTo("plan_extension_ready");
    assertThat(second.error).isInstanceOf(IllegalStateException.class);
    assertThat(proposalRepository.extensionRevisions).hasSize(1);
  }

  @org.junit.jupiter.api.Test
  void synchronousAgentStartupFailureAfterRevisionCreationStoresFailedAndEmitsPlanExtensionError() {
    LearningPlan plan = learningPlanRepository.save(activePlan(basePlan()));
    LearningPlanExtensionProposalStreamService service = serviceWithAgent(new ThrowingAgentLoopRunner());
    CollectingSubscriber subscriber = new CollectingSubscriber();

    service.streamFirstRevision(
        plan.userId(),
        plan.id(),
        "追加图论阶段",
        "run-startup-failure",
        Map.of()).subscribe(subscriber);
    subscriber.await();

    assertThat(subscriber.error).isNull();
    assertThat(subscriber.events).isNotEmpty();
    assertThat(subscriber.events.get(subscriber.events.size() - 1).eventName()).isEqualTo("plan_extension_error");
    LearningPlanExtensionRevision failed = proposalRepository.extensionRevisions.values().stream()
        .findFirst()
        .orElseThrow();
    assertThat(failed.status()).isEqualTo(LearningPlanProposalRevisionStatus.FAILED);
    assertThat(failed.errorCode()).isEqualTo("LEARNING_PLAN_EXTENSION_STREAM_FAILED");
  }

  private LearningPlanExtensionProposalStreamService serviceWithAgent(String content) {
    return serviceWithAgent(new CapturingAgentLoopRunner(content));
  }

  private LearningPlanExtensionProposalStreamService serviceWithAgent(AgentLoopRunner runner) {
    return new LearningPlanExtensionProposalStreamService(
        learningPlanRepository,
        proposalRepository,
        new LearningPlanProposalGroupService(proposalRepository, clock),
        practiceSessionRepository,
        new LearningPlanExtensionValidator(problemCatalog),
        runner,
        new org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder(new ObjectMapper()),
        new ObjectMapper(),
        TransactionOperations.withoutTransaction(),
        clock);
  }

  private LearningPlan activePlan(LearningPlanDraftPlan draftPlan) {
    Instant now = clock.instant();
    return new LearningPlan(12L, 42L, LearningPlanStatus.ACTIVE, draftPlan, now, now);
  }

  private LearningPlanProposalGroup activeExtensionGroup(long planId, String instruction) {
    Instant now = clock.instant();
    return new LearningPlanProposalGroup(
        null,
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        planId,
        LearningPlanProposalGroupStatus.ACTIVE,
        instruction,
        null,
        now,
        now);
  }

  private LearningPlanDraftPlan basePlan() {
    return new LearningPlanDraftPlan(
        "学习计划",
        "围绕数组和哈希表建立高频题能力。",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"),
        "当前水平：中级，每周 6 小时，语言：Java",
        List.of(new LearningPlanPhaseDraft(
            1,
            "数组与哈希表基础",
            2,
            "Array",
            List.of("掌握基础题型"),
            List.of("Array"),
            List.of("能独立复盘错题"),
            "记录边界条件。",
            List.of(new LearningPlanProblemDraft(
                "two-sum",
                1,
                "Two Sum",
                "两数之和",
                "EASY",
                List.of("Array"),
                "匹配数组训练目标。",
                1)))),
        Map.of("problemRecommendationIncomplete", false));
  }

  private LearningPlanExtensionDraft extensionDraft(String summary, String slug) {
    return new LearningPlanExtensionDraft(summary, List.of(extensionPhase(slug)), Map.of("problemRecommendationIncomplete", false));
  }

  private LearningPlanPhaseDraft extensionPhase(String slug) {
    return new LearningPlanPhaseDraft(
        2,
        "图论补强",
        1,
        "Graph",
        List.of("掌握图遍历"),
        List.of("Graph"),
        List.of("能识别连通性问题"),
        "复盘建图方式。",
        List.of(new LearningPlanProblemDraft(
            slug,
            261,
            slug,
            slug,
            "MEDIUM",
            List.of("Graph"),
            "补充图论训练。",
            1)));
  }

  private String extensionJson(String summary, String slug) {
    return """
        {
          "summary": "%s",
          "newPhases": [
            {
              "phaseIndex": 2,
              "title": "图论补强",
              "durationWeeks": 1,
              "focus": "Graph",
              "objectives": ["掌握图遍历"],
              "recommendedTags": ["Graph"],
              "acceptanceCriteria": ["能识别连通性问题"],
              "reviewAdvice": "复盘建图方式。",
              "problems": [
                {
                  "slug": "%s",
                  "frontendId": 261,
                  "title": "%s",
                  "titleCn": "%s",
                  "difficulty": "MEDIUM",
                  "tags": ["Graph"],
                  "reason": "补充图论训练。",
                  "sortOrder": 1
                }
              ]
            }
          ],
          "metadata": {
            "problemRecommendationIncomplete": false
          }
        }
        """.formatted(summary, slug, slug, slug);
  }

  private PracticeProgress progress(LearningPlan plan) {
    return new PracticeProgress(
        1L,
        plan.userId(),
        plan.id(),
        1,
        "two-sum",
        PracticeProgressStatus.COMPLETED,
        clock.instant(),
        clock.instant());
  }

  private List<LearningPlanProposalStreamEvent> collect(Flow.Publisher<LearningPlanProposalStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    assertThat(subscriber.error).isNull();
    return subscriber.events;
  }

  private List<LearningPlanProposalStreamEvent> collectAsync(Flow.Publisher<LearningPlanProposalStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    return subscriber.events;
  }

  private void waitUntilRevisionCount(int expectedCount) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (proposalRepository.extensionRevisions.size() >= expectedCount) {
        return;
      }
      Thread.yield();
    }
    throw new AssertionError("Timed out waiting for extension revision count " + expectedCount);
  }

  private void waitUntilDone(List<LearningPlanProposalStreamEvent> events, int minEvents) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      if (events.size() >= minEvents && events.get(events.size() - 1) instanceof LearningPlanProposalStreamEvent.Proposal) {
        return;
      }
      Thread.yield();
    }
    throw new AssertionError("Timed out waiting for stream completion event");
  }

  private List<String> lockOrder() {
    return locks;
  }

  private static class CapturingAgentLoopRunner extends AgentLoopRunner {
    private final String content;
    private final AtomicReference<AgentRequest> request = new AtomicReference<>();

    CapturingAgentLoopRunner(String content) {
      super(new org.congcong.algomentor.llm.core.gateway.LlmGateway() {
        @Override
        public org.congcong.algomentor.llm.core.response.LlmCompletionResult complete(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<LlmStreamEvent> stream(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }
      }, "test-model", org.congcong.algomentor.agent.core.AgentToolRegistry.empty(), 1);
      this.content = content;
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      this.request.set(request);
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        publisher.submit(new AgentStreamEvent.AgentStepStart(request.runId(), 1));
        publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta(content)));
        publisher.submit(new AgentStreamEvent.AgentStepEnd(request.runId(), 1, LlmFinishReason.STOP, 0));
        publisher.submit(new AgentStreamEvent.AgentRunEnd(request.runId(), 1, LlmFinishReason.STOP, Map.of()));
        publisher.close();
      };
    }
  }

  private static final class ManualAgentLoopRunner extends AgentLoopRunner {
    private final AtomicReference<Flow.Subscriber<? super AgentStreamEvent>> subscriber = new AtomicReference<>();
    private final AtomicReference<AgentRequest> request = new AtomicReference<>();

    ManualAgentLoopRunner() {
      super(new org.congcong.algomentor.llm.core.gateway.LlmGateway() {
        @Override
        public org.congcong.algomentor.llm.core.response.LlmCompletionResult complete(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<LlmStreamEvent> stream(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }
      }, "test-model", org.congcong.algomentor.agent.core.AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      this.request.set(request);
      return subscriber -> {
        this.subscriber.set(subscriber);
        subscriber.onSubscribe(new Flow.Subscription() {
          @Override
          public void request(long n) {
          }

          @Override
          public void cancel() {
          }
        });
      };
    }

    void complete(String content) {
      Flow.Subscriber<? super AgentStreamEvent> current = subscriber.get();
      AgentRequest currentRequest = request.get();
      current.onNext(new AgentStreamEvent.AgentStepStart(currentRequest.runId(), 1));
      current.onNext(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta(content)));
      current.onNext(new AgentStreamEvent.AgentStepEnd(currentRequest.runId(), 1, LlmFinishReason.STOP, 0));
      current.onNext(new AgentStreamEvent.AgentRunEnd(currentRequest.runId(), 1, LlmFinishReason.STOP, Map.of()));
      current.onComplete();
    }
  }

  private static final class ThrowingAgentLoopRunner extends AgentLoopRunner {

    ThrowingAgentLoopRunner() {
      super(new org.congcong.algomentor.llm.core.gateway.LlmGateway() {
        @Override
        public org.congcong.algomentor.llm.core.response.LlmCompletionResult complete(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<LlmStreamEvent> stream(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }
      }, "test-model", org.congcong.algomentor.agent.core.AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      throw new IllegalStateException("agent startup failed");
    }
  }

  private static class CollectingSubscriber implements Flow.Subscriber<LearningPlanProposalStreamEvent> {
    private final List<LearningPlanProposalStreamEvent> events = new ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private Throwable error;
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    @Override
    public void onNext(LearningPlanProposalStreamEvent item) {
      events.add(item);
      subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
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

  private static final class InMemoryLearningPlanRepository implements LearningPlanRepository {
    private final Map<Long, LearningPlan> plans = new HashMap<>();
    private final List<String> locks;

    private InMemoryLearningPlanRepository(List<String> locks) {
      this.locks = locks;
    }

    @Override
    public LearningPlan save(LearningPlan plan) {
      LearningPlan saved = plan.id() == null ? plan.withId(12L) : plan;
      plans.put(saved.id(), saved);
      return saved;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return plans.values().stream().filter(plan -> plan.userId() == userId).toList();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.ofNullable(plans.get(planId)).filter(plan -> plan.userId() == userId);
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUserForUpdate(long planId, long userId) {
      locks.add("plan:" + planId);
      return findPlanByIdForUser(planId, userId);
    }
  }

  private static final class InMemoryProposalRepository implements LearningPlanProposalRepository {
    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private final Map<Long, LearningPlanExtensionRevision> extensionRevisions = new HashMap<>();
    private final List<String> locks;
    private long groupSequence = 10;
    private long proposalSequence = 1000;

    private InMemoryProposalRepository(List<String> locks) {
      this.locks = locks;
    }

    @Override
    public LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group) {
      long id = group.id() == null ? groupSequence++ : group.id();
      LearningPlanProposalGroup saved = group.withId(id);
      groups.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId) {
      return Optional.ofNullable(groups.get(groupId)).filter(group -> group.userId() == userId);
    }

    @Override
    public Optional<LearningPlanProposalGroup> findGroupForUserForUpdate(long groupId, long userId) {
      locks.add("group:" + groupId);
      return findGroupForUser(groupId, userId);
    }

    @Override
    public Optional<LearningPlanProposalGroup> findLatestActiveGroup(
        long userId,
        LearningPlanProposalType proposalType,
        LearningPlanProposalTargetType targetType,
        long targetId) {
      return groups.values().stream()
          .filter(group -> group.userId() == userId)
          .filter(group -> group.proposalType() == proposalType)
          .filter(group -> group.targetType() == targetType)
          .filter(group -> group.targetId() == targetId)
          .filter(group -> group.status() == LearningPlanProposalGroupStatus.ACTIVE)
          .max(Comparator.comparing(LearningPlanProposalGroup::createdAt));
    }

    @Override
    public LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision) {
      long id = revision.id() == null ? proposalSequence++ : revision.id();
      LearningPlanDraftRevision saved = revision.withId(id);
      draftRevisions.put(id, saved);
      return saved;
    }

    @Override
    public LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision) {
      long id = revision.id() == null ? proposalSequence++ : revision.id();
      LearningPlanExtensionRevision saved = revision.withId(id);
      extensionRevisions.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(draftRevisions.get(revisionId)).filter(revision -> revision.userId() == userId);
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(extensionRevisions.get(revisionId)).filter(revision -> revision.userId() == userId);
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId) {
      return extensionRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .filter(revision -> revision.status() == LearningPlanProposalRevisionStatus.READY)
          .max(Comparator.comparingInt(LearningPlanExtensionRevision::revisionNo));
    }

    @Override
    public int nextRevisionNo(long proposalGroupId) {
      long draftCount = draftRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .count();
      long extensionCount = extensionRevisions.values().stream()
          .filter(revision -> revision.proposalGroupId() == proposalGroupId)
          .count();
      return (int) Math.max(draftCount, extensionCount) + 1;
    }

    @Override
    public List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }

    @Override
    public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      List<Long> superseded = new ArrayList<>();
      extensionRevisions.replaceAll((id, revision) -> {
        if (revision.proposalGroupId() == proposalGroupId
            && revision.id() != exceptRevisionId
            && revision.status() == LearningPlanProposalRevisionStatus.READY) {
          superseded.add(revision.id());
          return revision.withStatus(LearningPlanProposalRevisionStatus.SUPERSEDED, revision.updatedAt());
        }
        return revision;
      });
      return superseded;
    }
  }

  private static final class InMemoryPracticeSessionRepository implements PracticeSessionRepository {
    private final List<PracticeProgress> progress = new ArrayList<>();

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex, String problemSlug, String locale) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<PracticeProgress> findProgressByPlan(long userId, long planId) {
      return progress.stream()
          .filter(item -> item.userId() == userId)
          .filter(item -> item.planId() == planId)
          .toList();
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FakeProblemCatalog implements LearningPlanProblemCatalog {
    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of(
          candidate("graph-valid-tree"),
          candidate("number-of-islands"),
          candidate("two-sum"));
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return searchProblems(new LearningPlanProblemSearch(null, null, 10)).stream()
          .filter(candidate -> candidate.slug().equals(slug))
          .findFirst();
    }

    private static LearningPlanProblemCandidate candidate(String slug) {
      return new LearningPlanProblemCandidate(slug, 261, slug, slug, "MEDIUM", List.of("Graph"));
    }
  }
}
