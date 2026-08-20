package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentPreparedStream;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionCapabilities;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class LearningPlanDraftRevisionGenerationServiceTest {

  private static final long USER_ID = 7L;
  private static final long DRAFT_ID = 102L;
  private static final Instant NOW = Instant.parse("2026-08-20T08:00:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void reusesTheAcceptedRevisionWithoutPreparingAnotherAgentRun() {
    Fixture fixture = new Fixture(new RecordingPreparedStream());

    LearningPlanDraftRevisionGenerationStart first = fixture.service.start(
        USER_ID, DRAFT_ID, "  增加图论复习  ", "same-key");
    LearningPlanDraftRevisionGenerationStart replay = fixture.service.start(
        USER_ID, DRAFT_ID, "增加图论复习", "same-key");

    assertThat(first.newlyStarted()).isTrue();
    assertThat(replay.newlyStarted()).isFalse();
    assertThat(replay.revision().id()).isEqualTo(first.revision().id());
    assertThat(fixture.runtime.prepareCalls).isEqualTo(1);
    assertThat(fixture.proposals.draftRevisions).hasSize(1);
    assertThat(fixture.proposals.generationLocks).isEqualTo(2);
  }

  @Test
  void rejectsReusedKeyWithDifferentNormalizedInstructionBeforePreparingAnotherAgent() {
    Fixture fixture = new Fixture(new RecordingPreparedStream());
    fixture.service.start(USER_ID, DRAFT_ID, "增加图论复习", "same-key");

    assertThatThrownBy(() -> fixture.service.start(USER_ID, DRAFT_ID, "增加动态规划", "same-key"))
        .isInstanceOf(LearningPlanException.class)
        .extracting("code")
        .isEqualTo(LearningPlanDraftRevisionGenerationConstants.IDEMPOTENCY_CONFLICT_CODE);

    assertThat(fixture.runtime.prepareCalls).isEqualTo(1);
    assertThat(fixture.proposals.draftRevisions).hasSize(1);
  }

  @Test
  void startsAnIndependentSubscriberOnlyAfterSynchronousAgentAdmission() {
    RecordingPreparedStream prepared = new RecordingPreparedStream();
    Fixture fixture = new Fixture(prepared);

    LearningPlanDraftRevisionGenerationStart started = fixture.service.start(
        USER_ID, DRAFT_ID, "  增加图论复习  ", "generation-key");

    assertThat(started.revision().status()).isEqualTo(LearningPlanProposalRevisionStatus.GENERATING);
    assertThat(started.revision().generationRequestKey()).isEqualTo("generation-key");
    assertThat(started.revision().generationRequestFingerprint()).hasSize(64);
    assertThat(fixture.runtime.prepareCalls).isEqualTo(1);
    assertThat(prepared.subscriberCount).isEqualTo(1);
    assertThat(prepared.requested).isEqualTo(1);
    assertThat(fixture.events).isEmpty();
  }

  @Test
  void propagatesExecutorRejectionWithoutPublishingRealtimeEvents() {
    Fixture fixture = new Fixture(new RejectingPreparedStream());

    assertThatThrownBy(() -> fixture.service.start(USER_ID, DRAFT_ID, "增加图论复习", "generation-key"))
        .isInstanceOf(RejectedExecutionException.class);

    assertThat(fixture.events).isEmpty();
  }

  private static LearningPlanDraft draft() {
    return new LearningPlanDraft(
        DRAFT_ID,
        USER_ID,
        LearningPlanDraftSource.AI_PERSONALIZED,
        LearningPlanDraftStatus.GENERATED,
        brief(),
        List.of(),
        List.of(),
        "已生成学习计划草案。",
        plan(),
        null,
        NOW.plusSeconds(3_600),
        NOW,
        NOW);
  }

  private static LearningPlanBrief brief() {
    return new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 算法面试",
        15,
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        false,
        LearningPlanContentLocale.ZH_CN);
  }

  private static LearningPlanDraftPlan plan() {
    return new LearningPlanDraftPlan(
        "Java 算法面试计划",
        "围绕高频题型训练。",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        List.of(new LearningPlanPhaseDraft(1, "基础", 1, "数组", List.of())),
        Map.of());
  }

  private final class Fixture {

    private final InMemoryDraftRepository drafts = new InMemoryDraftRepository();
    private final InMemoryProposalRepository proposals = new InMemoryProposalRepository();
    private final RecordingAgentRuntime runtime;
    private final List<LearningPlanDraftRevisionGenerationEvent> events = new java.util.ArrayList<>();
    private final LearningPlanDraftRevisionGenerationService service;

    private Fixture(AgentPreparedStream prepared) {
      runtime = new RecordingAgentRuntime(prepared);
      drafts.save(draft());
      proposals.saveGroup(new LearningPlanProposalGroup(
          203L,
          USER_ID,
          LearningPlanProposalType.DRAFT_REVISION,
          LearningPlanProposalTargetType.DRAFT,
          DRAFT_ID,
          LearningPlanProposalGroupStatus.ACTIVE,
          "增加图论复习",
          null,
          NOW,
          NOW));
      service = new LearningPlanDraftRevisionGenerationService(
          drafts,
          proposals,
          new LearningPlanProposalGroupService(proposals, clock),
          runtime,
          new ObjectMapper(),
          new StartupOnlyTransactionOperations(),
          clock,
          new LearningPlanPersonalizationContextService(null),
          new LearningPlanAiRevisionAccessService(
              ignored -> new LearningPlanAiRevisionCapabilities(true, true, true)),
          (draftId, revisionId, event) -> events.add(event));
    }
  }

  private static final class InMemoryDraftRepository implements LearningPlanDraftRepository {

    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();

    @Override
    public LearningPlanDraft save(LearningPlanDraft value) {
      LearningPlanDraft saved = value.id() == null ? value.withId(DRAFT_ID) : value;
      drafts.put(saved.id(), saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(value -> value.userId() == userId);
    }
  }

  private static final class InMemoryProposalRepository implements LearningPlanProposalRepository {

    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private int generationLocks;
    private long revisionSequence = 401L;

    @Override
    public LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group) {
      LearningPlanProposalGroup saved = group.id() == null ? group.withId(203L) : group;
      groups.put(saved.id(), saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId) {
      return Optional.ofNullable(groups.get(groupId)).filter(value -> value.userId() == userId);
    }

    @Override
    public Optional<LearningPlanProposalGroup> findLatestActiveGroup(
        long userId,
        LearningPlanProposalType proposalType,
        LearningPlanProposalTargetType targetType,
        long targetId) {
      return groups.values().stream()
          .filter(value -> value.userId() == userId)
          .filter(value -> value.proposalType() == proposalType)
          .filter(value -> value.targetType() == targetType)
          .filter(value -> value.targetId() == targetId)
          .filter(value -> value.status() == LearningPlanProposalGroupStatus.ACTIVE)
          .findFirst();
    }

    @Override
    public LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision) {
      long revisionId = revision.id() == null ? revisionSequence++ : revision.id();
      LearningPlanDraftRevision saved = revision.withId(revisionId);
      draftRevisions.put(revisionId, saved);
      return saved;
    }

    @Override
    public LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId) {
      return Optional.ofNullable(draftRevisions.get(revisionId)).filter(value -> value.userId() == userId);
    }

    @Override
    public void lockDraftRevisionGenerationRequest(long userId, long draftId, String requestKey) {
      generationLocks++;
    }

    @Override
    public Optional<LearningPlanDraftRevision> findDraftRevisionByGenerationRequestKey(
        long userId, long draftId, String requestKey) {
      return draftRevisions.values().stream()
          .filter(value -> value.userId() == userId)
          .filter(value -> value.draftId() == draftId)
          .filter(value -> requestKey.equals(value.generationRequestKey()))
          .findFirst();
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId) {
      return Optional.empty();
    }

    @Override
    public Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId) {
      return Optional.empty();
    }

    @Override
    public int nextRevisionNo(long proposalGroupId) {
      return (int) draftRevisions.values().stream()
          .filter(value -> value.proposalGroupId() == proposalGroupId)
          .count() + 1;
    }

    @Override
    public List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }

    @Override
    public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }
  }

  private static final class RecordingAgentRuntime implements AgentRuntime {

    private final AgentPreparedStream prepared;
    private int prepareCalls;

    private RecordingAgentRuntime(AgentPreparedStream prepared) {
      this.prepared = prepared;
    }

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
      return prepared;
    }
  }

  private static final class StartupOnlyTransactionOperations implements TransactionOperations {

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      throw new AssertionError("terminal transaction must not run during startup");
    }
  }

  private static class RecordingPreparedStream implements AgentPreparedStream {

    private int subscriberCount;
    private long requested;

    @Override
    public long taskId() {
      return 1L;
    }

    @Override
    public String runUuid() {
      return "run";
    }

    @Override
    public boolean idempotentReplay() {
      return false;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super AgentStreamEvent> subscriber) {
      subscriberCount++;
      subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long count) {
          requested += count;
        }

        @Override
        public void cancel() {
        }
      });
    }
  }

  private static final class RejectingPreparedStream extends RecordingPreparedStream {

    @Override
    public void subscribe(Flow.Subscriber<? super AgentStreamEvent> subscriber) {
      throw new RejectedExecutionException("executor saturated");
    }
  }
}
