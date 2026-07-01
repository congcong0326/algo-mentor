package org.congcong.algomentor.mentor.application.learningplan.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.junit.jupiter.api.Test;

class LearningPlanExtensionApplyServiceTest {

  private static final long USER_ID = 7L;
  private static final long PLAN_ID = 12L;
  private static final Instant NOW = Instant.parse("2026-07-01T00:00:00Z");

  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final InMemoryProposalRepository proposalRepository = new InMemoryProposalRepository();
  private final InMemoryLearningPlanRepository learningPlanRepository = new InMemoryLearningPlanRepository();
  private final FakePracticeSessionRepository practiceSessionRepository = new FakePracticeSessionRepository();
  private final LearningPlanExtensionApplyService service = new LearningPlanExtensionApplyService(
      proposalRepository,
      learningPlanRepository,
      practiceSessionRepository,
      new LearningPlanExtensionValidator(new FakeProblemCatalog()),
      clock);

  @Test
  void latestReadyRevisionAppliesAndMarksGroupAndRevisionApplied() {
    learningPlanRepository.save(plan(phase(1, "two-sum")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);
    LearningPlanExtensionRevision revision = saveReadyRevision(group.id(), 1, 1, extension(phase(2, "graph-valid-tree")));
    saveActiveGroup(group.withLatestProposalId(revision.id(), NOW));

    LearningPlanExtensionApplyResult result = service.apply(USER_ID, PLAN_ID, group.id());

    assertThat(result.planId()).isEqualTo(PLAN_ID);
    assertThat(result.proposalGroupId()).isEqualTo(group.id());
    assertThat(result.proposalId()).isEqualTo(revision.id());
    assertThat(result.status()).isEqualTo(LearningPlanProposalGroupStatus.APPLIED);
    assertThat(result.appendedPhaseCount()).isEqualTo(1);
    assertThat(proposalRepository.findGroupForUser(group.id(), USER_ID).orElseThrow().status())
        .isEqualTo(LearningPlanProposalGroupStatus.APPLIED);
    LearningPlanExtensionRevision appliedRevision = proposalRepository.findExtensionRevisionForUser(revision.id(), USER_ID)
        .orElseThrow();
    assertThat(appliedRevision.status()).isEqualTo(LearningPlanProposalRevisionStatus.APPLIED);
    assertThat(appliedRevision.appliedAt()).isEqualTo(NOW);
    assertThat(appliedRevision.proposedExtension().newPhases()).extracting(LearningPlanPhaseDraft::phaseIndex)
        .containsExactly(2);
    assertThat(learningPlanRepository.findPlanByIdForUser(PLAN_ID, USER_ID).orElseThrow().plan().phases())
        .extracting(LearningPlanPhaseDraft::phaseIndex)
        .containsExactly(1, 2);
  }

  @Test
  void noLatestReadyRevisionThrowsNotLatest() {
    learningPlanRepository.save(plan(phase(1, "two-sum")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);

    assertThatThrownBy(() -> service.apply(USER_ID, PLAN_ID, group.id()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("没有可应用的最新学习计划扩展提案。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_NOT_LATEST");
  }

  @Test
  void nonLatestReadyRevisionThrowsNotLatest() {
    learningPlanRepository.save(plan(phase(1, "two-sum")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);
    LearningPlanExtensionRevision readyRevision = saveReadyRevision(
        group.id(),
        1,
        1,
        extension(phase(2, "graph-valid-tree")));
    LearningPlanExtensionRevision failedRevision = proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        group.id(),
        PLAN_ID,
        USER_ID,
        2,
        LearningPlanProposalRevisionStatus.FAILED,
        "增加图论题",
        draftPlan(List.of(phase(1, "two-sum"))),
        null,
        1,
        null,
        null,
        null,
        "AI_TIMEOUT",
        "生成失败",
        NOW,
        NOW));
    saveActiveGroup(group.withLatestProposalId(failedRevision.id(), NOW));

    assertThatThrownBy(() -> service.apply(USER_ID, PLAN_ID, group.id()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("没有可应用的最新学习计划扩展提案。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_NOT_LATEST");
    assertThat(proposalRepository.findExtensionRevisionForUser(readyRevision.id(), USER_ID).orElseThrow().status())
        .isEqualTo(LearningPlanProposalRevisionStatus.READY);
  }

  @Test
  void duplicateProblemAfterAnotherExtensionThrowsConflict() {
    learningPlanRepository.save(plan(phase(1, "two-sum"), phase(2, "graph-valid-tree")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);
    LearningPlanExtensionRevision revision = saveReadyRevision(group.id(), 1, 1, extension(phase(2, "graph-valid-tree")));
    saveActiveGroup(group.withLatestProposalId(revision.id(), NOW));

    assertThatThrownBy(() -> service.apply(USER_ID, PLAN_ID, group.id()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展提案与当前学习计划冲突，请重新生成。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_EXTENSION_CONFLICT");
  }

  @Test
  void maxPhaseIndexDriftRenumbersNewPhasesToCurrentMaxPlusOne() {
    learningPlanRepository.save(plan(phase(1, "two-sum"), phase(2, "number-of-islands")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);
    LearningPlanExtensionRevision revision = saveReadyRevision(group.id(), 1, 1, extension(phase(2, "graph-valid-tree")));
    saveActiveGroup(group.withLatestProposalId(revision.id(), NOW));

    LearningPlanExtensionApplyResult result = service.apply(USER_ID, PLAN_ID, group.id());

    assertThat(result.appendedPhaseCount()).isEqualTo(1);
    List<LearningPlanPhaseDraft> phases = learningPlanRepository.findPlanByIdForUser(PLAN_ID, USER_ID)
        .orElseThrow()
        .plan()
        .phases();
    assertThat(phases).extracting(LearningPlanPhaseDraft::phaseIndex).containsExactly(1, 2, 3);
    assertThat(phases).flatExtracting(LearningPlanPhaseDraft::problems)
        .extracting(LearningPlanProblemDraft::slug)
        .containsExactly("two-sum", "number-of-islands", "graph-valid-tree");
    assertThat(proposalRepository.findExtensionRevisionForUser(revision.id(), USER_ID).orElseThrow()
        .proposedExtension().newPhases())
        .extracting(LearningPlanPhaseDraft::phaseIndex)
        .containsExactly(3);
  }

  @Test
  void noPhaseIndexDriftDoesNotNormalizeInvalidReadyRevision() {
    learningPlanRepository.save(plan(phase(1, "two-sum")));
    LearningPlanProposalGroup group = saveActiveGroup((Long) null);
    LearningPlanExtensionRevision revision = saveReadyRevision(
        group.id(),
        1,
        1,
        extension(phase(2, "graph-valid-tree"), phase(2, "number-of-islands")));
    saveActiveGroup(group.withLatestProposalId(revision.id(), NOW));

    assertThatThrownBy(() -> service.apply(USER_ID, PLAN_ID, group.id()))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("扩展阶段编号不能重复。")
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_EXTENSION_INVALID");
    assertThat(learningPlanRepository.appendCount).isZero();
    assertThat(learningPlanRepository.findPlanByIdForUser(PLAN_ID, USER_ID).orElseThrow().plan().phases())
        .extracting(LearningPlanPhaseDraft::phaseIndex)
        .containsExactly(1);
    assertThat(proposalRepository.findGroupForUser(group.id(), USER_ID).orElseThrow().status())
        .isEqualTo(LearningPlanProposalGroupStatus.ACTIVE);
    assertThat(proposalRepository.findExtensionRevisionForUser(revision.id(), USER_ID).orElseThrow().status())
        .isEqualTo(LearningPlanProposalRevisionStatus.READY);
  }

  private LearningPlanProposalGroup saveActiveGroup(Long latestProposalId) {
    return saveActiveGroup(new LearningPlanProposalGroup(
        null,
        USER_ID,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        PLAN_ID,
        LearningPlanProposalGroupStatus.ACTIVE,
        "增加图论题",
        latestProposalId,
        NOW,
        NOW));
  }

  private LearningPlanProposalGroup saveActiveGroup(LearningPlanProposalGroup group) {
    return proposalRepository.saveGroup(group);
  }

  private LearningPlanExtensionRevision saveReadyRevision(
      long proposalGroupId,
      int revisionNo,
      int baseMaxPhaseIndex,
      LearningPlanExtensionDraft extension) {
    return proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        proposalGroupId,
        PLAN_ID,
        USER_ID,
        revisionNo,
        LearningPlanProposalRevisionStatus.READY,
        "增加图论题",
        draftPlan(List.of(phase(1, "two-sum"))),
        null,
        baseMaxPhaseIndex,
        null,
        extension,
        null,
        null,
        null,
        NOW,
        NOW));
  }

  private static LearningPlan plan(LearningPlanPhaseDraft... phases) {
    return new LearningPlan(
        PLAN_ID,
        USER_ID,
        LearningPlanStatus.ACTIVE,
        draftPlan(List.of(phases)),
        NOW,
        NOW);
  }

  private static LearningPlanExtensionDraft extension(LearningPlanPhaseDraft... phases) {
    return new LearningPlanExtensionDraft("追加练习", List.of(phases), Map.of("source", "test"));
  }

  private static LearningPlanDraftPlan draftPlan(List<LearningPlanPhaseDraft> phases) {
    return new LearningPlanDraftPlan(
        "学习计划",
        "基础训练",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Graph"),
        "已有基础",
        phases,
        Map.of());
  }

  private static LearningPlanPhaseDraft phase(int phaseIndex, String... slugs) {
    List<LearningPlanProblemDraft> problems = java.util.stream.IntStream.range(0, slugs.length)
        .mapToObj(index -> new LearningPlanProblemDraft(
            slugs[index],
            index + 1,
            slugs[index],
            slugs[index],
            "MEDIUM",
            List.of("Graph"),
            "练习",
            index + 1))
        .toList();
    return new LearningPlanPhaseDraft(
        phaseIndex,
        "阶段 " + phaseIndex,
        1,
        "图论",
        List.of("掌握图论"),
        List.of("Graph"),
        List.of("完成练习"),
        "复盘模板",
        problems);
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {

    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of(
          candidate("graph-valid-tree"),
          candidate("number-of-islands"));
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      return searchProblems(new LearningPlanProblemSearch(null, null, 10)).stream()
          .filter(candidate -> candidate.slug().equals(slug))
          .findFirst();
    }

    private static LearningPlanProblemCandidate candidate(String slug) {
      return new LearningPlanProblemCandidate(slug, 1, slug, slug, "MEDIUM", List.of("Graph"));
    }
  }

  private static class InMemoryLearningPlanRepository implements LearningPlanRepository {

    private final Map<Long, LearningPlan> plans = new HashMap<>();
    private int appendCount;

    @Override
    public LearningPlan save(LearningPlan plan) {
      plans.put(plan.id(), plan);
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return plans.values().stream()
          .filter(plan -> plan.userId() == userId)
          .toList();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.ofNullable(plans.get(planId)).filter(plan -> plan.userId() == userId);
    }

    @Override
    public LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases) {
      appendCount++;
      LearningPlan current = findPlanByIdForUser(planId, userId).orElseThrow();
      List<LearningPlanPhaseDraft> existingPhases = current.plan().phases();
      List<LearningPlanPhaseDraft> mergedPhases = new ArrayList<>(existingPhases);
      mergedPhases.addAll(newPhases);
      assertThat(mergedPhases).startsWith(existingPhases.toArray(LearningPlanPhaseDraft[]::new));
      LearningPlan updated = new LearningPlan(
          current.id(),
          current.userId(),
          current.status(),
          draftPlan(mergedPhases),
          current.createdAt(),
          current.updatedAt());
      plans.put(planId, updated);
      return updated;
    }
  }

  private static class InMemoryProposalRepository implements LearningPlanProposalRepository {
    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private final Map<Long, LearningPlanExtensionRevision> extensionRevisions = new HashMap<>();
    private long groupSequence = 10;
    private long proposalSequence = 100;

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
      return List.of();
    }

    @Override
    public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
      return List.of();
    }
  }

  private static class FakePracticeSessionRepository implements PracticeSessionRepository {

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale) {
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
      return List.of();
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      throw new UnsupportedOperationException();
    }
  }
}
