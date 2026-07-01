package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftPromptBuilder;

class LearningPlanDraftRevisionStreamServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final InMemoryProposalRepository proposalRepository = new InMemoryProposalRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();

  @org.junit.jupiter.api.Test
  void storesReadyRevisionSupersedesPreviousReadyRevisionAndReplacesDraftPlan() {
    LearningPlanDraft draft = draftRepository.save(generatedDraft(basePlan("原计划")));
    LearningPlanProposalGroup group = proposalRepository.saveGroup(activeDraftRevisionGroup(draft.id(), "先减少动态规划"));
    LearningPlanDraftRevision previous = proposalRepository.saveDraftRevision(new LearningPlanDraftRevision(
        null,
        group.id(),
        draft.id(),
        draft.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        "先减少动态规划",
        draft.draftPlan(),
        null,
        null,
        null,
        clock.instant(),
        clock.instant())).withReady(basePlan("旧修订计划"), clock.instant());
    previous = proposalRepository.saveDraftRevision(previous);
    LearningPlanDraftRevisionStreamService service = serviceWithAgent(finalJson("修订后计划"));

    List<LearningPlanProposalStreamEvent> events = collect(service.stream(
        draft.userId(),
        draft.id(),
        "减少动态规划题，增加数组基础题",
        "run-revision-1",
        Map.of("source", "test")));

    LearningPlanProposalStreamEvent finalEvent = events.get(events.size() - 1);
    assertThat(finalEvent.eventName()).isEqualTo("draft_revision_ready");
    LearningPlanProposalStreamEvent.Proposal proposalEvent = (LearningPlanProposalStreamEvent.Proposal) finalEvent;
    LearningPlanProposalEvent.DraftRevisionReady ready =
        (LearningPlanProposalEvent.DraftRevisionReady) proposalEvent.event();
    assertThat(ready.result().status()).isEqualTo(LearningPlanProposalRevisionStatus.READY);
    assertThat(ready.result().proposalGroupId()).isEqualTo(group.id());
    assertThat(ready.result().revisionNo()).isEqualTo(2);
    assertThat(ready.result().supersededProposalIds()).containsExactly(previous.id());
    assertThat(proposalRepository.draftRevisions.get(previous.id()).status())
        .isEqualTo(LearningPlanProposalRevisionStatus.SUPERSEDED);
    assertThat(proposalRepository.draftRevisions.get(ready.result().proposalId()).status())
        .isEqualTo(LearningPlanProposalRevisionStatus.READY);
    assertThat(proposalRepository.groups.get(group.id()).latestProposalId()).isEqualTo(ready.result().proposalId());
    assertThat(draftRepository.findDraftByIdForUser(draft.id(), draft.userId()).orElseThrow().draftPlan().title())
        .isEqualTo("修订后计划");
  }

  @org.junit.jupiter.api.Test
  void storesFailedRevisionAndEmitsDraftRevisionErrorWhenOutputIsInvalid() {
    LearningPlanDraft draft = draftRepository.save(generatedDraft(basePlan("原计划")));
    LearningPlanDraftRevisionStreamService service = serviceWithAgent("{\"title\":\"缺少阶段\"}");

    List<LearningPlanProposalStreamEvent> events = collect(service.stream(
        draft.userId(),
        draft.id(),
        "减少动态规划题",
        "run-revision-2",
        Map.of()));

    assertThat(events).extracting(LearningPlanProposalStreamEvent::eventName).contains("draft_revision_error");
    LearningPlanDraftRevision failed = proposalRepository.draftRevisions.values().stream()
        .filter(revision -> revision.draftId() == draft.id())
        .findFirst()
        .orElseThrow();
    assertThat(failed.status()).isEqualTo(LearningPlanProposalRevisionStatus.FAILED);
    assertThat(failed.errorCode()).isEqualTo("LEARNING_PLAN_DRAFT_INVALID");
    assertThat(draftRepository.findDraftByIdForUser(draft.id(), draft.userId()).orElseThrow().draftPlan().title())
        .isEqualTo("原计划");
  }

  private LearningPlanDraftRevisionStreamService serviceWithAgent(String content) {
    return new LearningPlanDraftRevisionStreamService(
        draftRepository,
        proposalRepository,
        new LearningPlanProposalGroupService(proposalRepository, clock),
        new LearningPlanDraftValidator(),
        new FakeAgentLoopRunner(content),
        new LearningPlanDraftPromptBuilder(),
        new ObjectMapper(),
        problemCatalog,
        clock);
  }

  private LearningPlanDraft generatedDraft(LearningPlanDraftPlan plan) {
    Instant now = clock.instant();
    return new LearningPlanDraft(
        null,
        42L,
        LearningPlanDraftStatus.GENERATED,
        command(),
        List.of("初始需求"),
        List.of(),
        "已生成学习计划草案。",
        plan,
        null,
        now.plus(14, ChronoUnit.DAYS),
        now,
        now);
  }

  private LearningPlanProposalGroup activeDraftRevisionGroup(long draftId, String instruction) {
    Instant now = clock.instant();
    return new LearningPlanProposalGroup(
        null,
        42L,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        draftId,
        LearningPlanProposalGroupStatus.ACTIVE,
        instruction,
        null,
        now,
        now);
  }

  private LearningPlanDraftCommand command() {
    return new LearningPlanDraftCommand(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"));
  }

  private LearningPlanDraftPlan basePlan(String title) {
    return new LearningPlanDraftPlan(
        title,
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
        List.of(
            phase(1, 2, "数组与哈希表基础"),
            phase(2, 1, "二分与双指针"),
            phase(3, 1, "动态规划入门")),
        Map.of("problemRecommendationIncomplete", false));
  }

  private LearningPlanPhaseDraft phase(int phaseIndex, int weeks, String title) {
    return new LearningPlanPhaseDraft(
        phaseIndex,
        title,
        weeks,
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
            1)));
  }

  private String finalJson(String title) {
    return """
        {
          "title": "%s",
          "summary": "围绕数组和哈希表建立高频题能力。",
          "intent": "INTERVIEW_SPRINT",
          "goal": "准备 Java 后端算法面试",
          "durationWeeks": 4,
          "level": "INTERMEDIATE",
          "weeklyHours": 6,
          "programmingLanguage": "Java",
          "difficultyPreference": "MEDIUM",
          "interviewOriented": true,
          "topicPreferences": ["Array"],
          "profileSummary": "当前水平：中级，每周 6 小时，语言：Java",
          "phases": [
            {
              "phaseIndex": 1,
              "title": "数组与哈希表基础",
              "durationWeeks": 2,
              "focus": "Array",
              "objectives": ["掌握数组基础题型"],
              "recommendedTags": ["Array"],
              "acceptanceCriteria": ["能独立复盘错题"],
              "reviewAdvice": "记录边界条件。",
              "problems": [
                {
                  "slug": "two-sum",
                  "frontendId": 1,
                  "title": "Two Sum",
                  "titleCn": "两数之和",
                  "difficulty": "EASY",
                  "tags": ["Array"],
                  "reason": "匹配数组训练目标。",
                  "sortOrder": 1
                }
              ]
            },
            {
              "phaseIndex": 2,
              "title": "二分与双指针",
              "durationWeeks": 1,
              "focus": "Binary Search",
              "objectives": ["掌握二分"],
              "recommendedTags": ["Binary Search"],
              "acceptanceCriteria": ["能说明循环不变量"],
              "reviewAdvice": "总结模板。",
              "problems": []
            },
            {
              "phaseIndex": 3,
              "title": "动态规划入门",
              "durationWeeks": 1,
              "focus": "Dynamic Programming",
              "objectives": ["识别状态转移"],
              "recommendedTags": ["Dynamic Programming"],
              "acceptanceCriteria": ["能写出状态定义"],
              "reviewAdvice": "复盘状态设计。",
              "problems": []
            }
          ],
          "metadata": {
            "problemRecommendationIncomplete": false
          }
        }
        """.formatted(title);
  }

  private List<LearningPlanProposalStreamEvent> collect(Flow.Publisher<LearningPlanProposalStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    return subscriber.events;
  }

  private static class FakeAgentLoopRunner extends AgentLoopRunner {
    private final String content;

    FakeAgentLoopRunner(String content) {
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

  private static class CollectingSubscriber implements Flow.Subscriber<LearningPlanProposalStreamEvent> {
    private final List<LearningPlanProposalStreamEvent> events = new ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
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

  private static final class InMemoryDraftRepository implements LearningPlanDraftRepository {
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

  private static final class InMemoryProposalRepository implements LearningPlanProposalRepository {
    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private final Map<Long, LearningPlanExtensionRevision> extensionRevisions = new HashMap<>();
    private long groupSequence = 10;
    private long proposalSequence = 1000;

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
      List<Long> superseded = new ArrayList<>();
      draftRevisions.replaceAll((id, revision) -> {
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

  private static final class FakeProblemCatalog implements LearningPlanProblemCatalog {
    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of(candidate());
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      if ("two-sum".equals(slug)) {
        return Optional.of(candidate());
      }
      return Optional.empty();
    }

    private static LearningPlanProblemCandidate candidate() {
      return new LearningPlanProblemCandidate(
          "two-sum",
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array"));
    }
  }
}
