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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.junit.jupiter.api.Test;

class LearningPlanProposalGroupServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryProposalRepository repository = new InMemoryProposalRepository();
  private final LearningPlanProposalGroupService service = new LearningPlanProposalGroupService(repository, clock);

  @Test
  void createsDraftRevisionGroupAsActive() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        100L,
        "减少动态规划题");

    assertThat(group.id()).isPositive();
    assertThat(group.userId()).isEqualTo(42L);
    assertThat(group.proposalType()).isEqualTo(LearningPlanProposalType.DRAFT_REVISION);
    assertThat(group.targetType()).isEqualTo(LearningPlanProposalTargetType.DRAFT);
    assertThat(group.targetId()).isEqualTo(100L);
    assertThat(group.status()).isEqualTo(LearningPlanProposalGroupStatus.ACTIVE);
    assertThat(group.initialInstruction()).isEqualTo("减少动态规划题");
    assertThat(group.latestProposalId()).isNull();
  }

  @Test
  void normalizesExtensionDraftSummary() {
    assertThat(new LearningPlanExtensionDraft(null, null, null).summary()).isEqualTo("");
    assertThat(new LearningPlanExtensionDraft("  追加图论练习  ", null, null).summary()).isEqualTo("追加图论练习");
  }

  @Test
  void discardsActiveExtensionProposalForUserAndPlan() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        900L,
        "增加图论题");

    LearningPlanProposalGroup discarded = service.discardExtensionProposal(42L, 900L, group.id());

    assertThat(discarded.status()).isEqualTo(LearningPlanProposalGroupStatus.DISCARDED);
    assertThat(repository.findGroupForUser(group.id(), 42L)).get()
        .extracting(LearningPlanProposalGroup::status)
        .isEqualTo(LearningPlanProposalGroupStatus.DISCARDED);
    assertThat(discarded.updatedAt()).isEqualTo(clock.instant());
  }

  @Test
  void rejectsDiscardWhenExtensionProposalDoesNotMatchPlan() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        900L,
        "增加图论题");

    assertThatThrownBy(() -> service.discardExtensionProposal(42L, 901L, group.id()))
        .isInstanceOf(org.congcong.algomentor.mentor.application.learningplan.LearningPlanException.class)
        .hasMessage("学习计划扩展提案组与请求不匹配。");
  }

  @Test
  void rejectsDiscardWhenGroupWasAppliedBeforeTransition() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        900L,
        "增加图论题");
    repository.saveGroup(group.withStatus(LearningPlanProposalGroupStatus.APPLIED, clock.instant()));

    assertThatThrownBy(() -> service.discardExtensionProposal(42L, 900L, group.id()))
        .isInstanceOf(org.congcong.algomentor.mentor.application.learningplan.LearningPlanException.class)
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_GROUP_NOT_ACTIVE");
  }

  @Test
  void staleDiscardTransitionDoesNotOverwriteAppliedGroup() {
    LearningPlanProposalGroup group = service.createGroup(
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        900L,
        "增加图论题");
    repository.applyAfterNextLock(group.id());

    assertThatThrownBy(() -> service.discardExtensionProposal(42L, 900L, group.id()))
        .isInstanceOf(org.congcong.algomentor.mentor.application.learningplan.LearningPlanException.class)
        .extracting("code")
        .isEqualTo("LEARNING_PLAN_PROPOSAL_GROUP_NOT_ACTIVE");
    assertThat(repository.findGroupForUser(group.id(), 42L)).get()
        .extracting(LearningPlanProposalGroup::status)
        .isEqualTo(LearningPlanProposalGroupStatus.APPLIED);
  }

  @Test
  void rejectsInvalidProposalTargetPair() {
    Instant now = clock.instant();

    assertThatThrownBy(() -> new LearningPlanProposalGroup(
        null,
        42L,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.PLAN,
        100L,
        LearningPlanProposalGroupStatus.ACTIVE,
        "减少动态规划题",
        null,
        now,
        now)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new LearningPlanProposalGroup(
        null,
        42L,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.DRAFT,
        100L,
        LearningPlanProposalGroupStatus.ACTIVE,
        "增加图论题",
        null,
        now,
        now)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsReadyRevisionsWithoutProposedContent() {
    Instant now = clock.instant();
    LearningPlanDraftPlan basePlan = draftPlan();

    assertThatThrownBy(() -> new LearningPlanDraftRevision(
        null,
        10L,
        100L,
        42L,
        1,
        LearningPlanProposalRevisionStatus.READY,
        "减少动态规划题",
        basePlan,
        null,
        null,
        null,
        now,
        now)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new LearningPlanExtensionRevision(
        null,
        10L,
        200L,
        42L,
        1,
        LearningPlanProposalRevisionStatus.READY,
        "增加图论题",
        basePlan,
        null,
        3,
        null,
        null,
        null,
        null,
        null,
        now,
        now)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsAppliedExtensionRevisionWithoutAppliedAt() {
    Instant now = clock.instant();

    assertThatThrownBy(() -> new LearningPlanExtensionRevision(
        null,
        10L,
        200L,
        42L,
        1,
        LearningPlanProposalRevisionStatus.APPLIED,
        "增加图论题",
        draftPlan(),
        null,
        3,
        null,
        new LearningPlanExtensionDraft("图论扩展", null, null),
        null,
        null,
        null,
        now,
        now)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFailedRevisionsWithoutFailureDetails() {
    Instant now = clock.instant();
    LearningPlanDraftPlan basePlan = draftPlan();

    assertThatThrownBy(() -> new LearningPlanDraftRevision(
        null,
        10L,
        100L,
        42L,
        1,
        LearningPlanProposalRevisionStatus.FAILED,
        "减少动态规划题",
        basePlan,
        null,
        " ",
        "生成失败",
        now,
        now)).isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new LearningPlanExtensionRevision(
        null,
        10L,
        200L,
        42L,
        1,
        LearningPlanProposalRevisionStatus.FAILED,
        "增加图论题",
        basePlan,
        null,
        3,
        null,
        null,
        null,
        "AI_TIMEOUT",
        " ",
        now,
        now)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullServiceDependencies() {
    assertThatThrownBy(() -> new LearningPlanProposalGroupService(null, clock))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new LearningPlanProposalGroupService(repository, null))
        .isInstanceOf(NullPointerException.class);
  }

  private static LearningPlanDraftPlan draftPlan() {
    return new LearningPlanDraftPlan(
        "学习计划",
        "基础训练",
        null,
        "提升算法能力",
        4,
        null,
        6,
        "Java",
        null,
        true,
        null,
        "已有基础",
        null,
        null);
  }

  private static final class InMemoryProposalRepository implements LearningPlanProposalRepository {
    private final Map<Long, LearningPlanProposalGroup> groups = new HashMap<>();
    private final Map<Long, LearningPlanDraftRevision> draftRevisions = new HashMap<>();
    private final Map<Long, LearningPlanExtensionRevision> extensionRevisions = new HashMap<>();
    private long groupSequence = 10;
    private long proposalSequence = 100;
    private Long applyAfterNextLockGroupId;

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
      Optional<LearningPlanProposalGroup> group = findGroupForUser(groupId, userId);
      if (applyAfterNextLockGroupId != null && applyAfterNextLockGroupId == groupId) {
        applyAfterNextLockGroupId = null;
        group.ifPresent(current -> groups.put(groupId, current.withStatus(
            LearningPlanProposalGroupStatus.APPLIED,
            current.updatedAt())));
      }
      return group;
    }

    @Override
    public LearningPlanProposalGroup discardActiveExtensionProposalGroup(
        long userId,
        long planId,
        long proposalGroupId,
        Instant updatedAt) {
      LearningPlanProposalGroup current = groups.get(proposalGroupId);
      if (current == null
          || current.userId() != userId
          || current.proposalType() != LearningPlanProposalType.PLAN_EXTENSION
          || current.targetType() != LearningPlanProposalTargetType.PLAN
          || current.targetId() != planId
          || current.status() != LearningPlanProposalGroupStatus.ACTIVE) {
        return null;
      }
      LearningPlanProposalGroup discarded = current.withStatus(LearningPlanProposalGroupStatus.DISCARDED, updatedAt);
      groups.put(proposalGroupId, discarded);
      return discarded;
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

    private void applyAfterNextLock(long groupId) {
      this.applyAfterNextLockGroupId = groupId;
    }
  }
}
