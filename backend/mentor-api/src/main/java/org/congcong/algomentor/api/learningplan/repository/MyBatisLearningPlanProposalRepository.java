package org.congcong.algomentor.api.learningplan.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanExtensionRevisionRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanProposalGroupRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisLearningPlanProposalRepository implements LearningPlanProposalRepository {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };

  private final LearningPlanMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisLearningPlanProposalRepository(LearningPlanMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public LearningPlanProposalGroup saveGroup(LearningPlanProposalGroup group) {
    LearningPlanProposalGroupRow row = toGroupRow(group);
    long groupId = row.id() == null ? 0 : row.id();
    if (group.id() == null) {
      groupId = mapper.insertProposalGroup(row);
    } else {
      mapper.updateProposalGroup(row);
    }
    return toGroup(mapper.findProposalGroupForUser(groupId, group.userId()));
  }

  @Override
  public Optional<LearningPlanProposalGroup> findGroupForUser(long groupId, long userId) {
    return Optional.ofNullable(mapper.findProposalGroupForUser(groupId, userId)).map(this::toGroup);
  }

  @Override
  public Optional<LearningPlanProposalGroup> findGroupForUserForUpdate(long groupId, long userId) {
    return Optional.ofNullable(mapper.lockProposalGroupForUpdate(groupId, userId)).map(this::toGroup);
  }

  @Override
  public Optional<LearningPlanProposalGroup> findLatestActiveGroup(
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId) {
    return Optional.ofNullable(mapper.findLatestActiveProposalGroup(
        userId,
        proposalType.name(),
        targetType.name(),
        targetId)).map(this::toGroup);
  }

  @Override
  @Transactional
  public LearningPlanDraftRevision saveDraftRevision(LearningPlanDraftRevision revision) {
    LearningPlanProposalGroupRow group = lockAndValidateGroup(
        revision.proposalGroupId(),
        revision.userId(),
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        revision.draftId());
    LearningPlanDraftRevisionRow row = toDraftRevisionRow(revision);
    long revisionId = row.id() == null ? 0 : row.id();
    if (revision.id() == null) {
      row = toDraftRevisionRow(withRevisionNo(revision, nextRevisionNoAfterLock(group.id())));
      revisionId = mapper.insertDraftRevision(row);
    } else {
      LearningPlanDraftRevisionRow existing = mapper.findDraftRevisionForUser(revision.id(), revision.userId());
      validateExistingDraftRevision(existing, revision);
      row = toDraftRevisionRow(withRevisionNo(revision, existing.revisionNo()));
      mapper.updateDraftRevision(row);
    }
    return toDraftRevision(mapper.findDraftRevisionForUser(revisionId, revision.userId()));
  }

  @Override
  @Transactional
  public LearningPlanExtensionRevision saveExtensionRevision(LearningPlanExtensionRevision revision) {
    LearningPlanProposalGroupRow group = lockAndValidateGroup(
        revision.proposalGroupId(),
        revision.userId(),
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        revision.planId());
    LearningPlanExtensionRevisionRow row = toExtensionRevisionRow(revision);
    long revisionId = row.id() == null ? 0 : row.id();
    if (revision.id() == null) {
      row = toExtensionRevisionRow(withRevisionNo(revision, nextRevisionNoAfterLock(group.id())));
      revisionId = mapper.insertExtensionRevision(row);
    } else {
      LearningPlanExtensionRevisionRow existing = mapper.findExtensionRevisionForUser(revision.id(), revision.userId());
      validateExistingExtensionRevision(existing, revision);
      row = toExtensionRevisionRow(withRevisionNo(revision, existing.revisionNo()));
      mapper.updateExtensionRevision(row);
    }
    return toExtensionRevision(mapper.findExtensionRevisionForUser(revisionId, revision.userId()));
  }

  @Override
  public Optional<LearningPlanDraftRevision> findDraftRevisionForUser(long revisionId, long userId) {
    return Optional.ofNullable(mapper.findDraftRevisionForUser(revisionId, userId)).map(this::toDraftRevision);
  }

  @Override
  public Optional<LearningPlanExtensionRevision> findExtensionRevisionForUser(long revisionId, long userId) {
    return Optional.ofNullable(mapper.findExtensionRevisionForUser(revisionId, userId)).map(this::toExtensionRevision);
  }

  @Override
  public Optional<LearningPlanExtensionRevision> findLatestReadyExtensionRevision(long proposalGroupId) {
    return Optional.ofNullable(mapper.findLatestReadyExtensionRevision(proposalGroupId)).map(this::toExtensionRevision);
  }

  @Override
  @Transactional
  public int nextRevisionNo(long proposalGroupId) {
    LearningPlanProposalGroupRow group = mapper.lockProposalGroupByIdForUpdate(proposalGroupId);
    if (group == null) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND", "学习计划提案组不存在。");
    }
    return nextRevisionNoAfterLock(proposalGroupId);
  }

  @Override
  @Transactional
  public List<Long> markReadyDraftRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
    return mapper.markReadyDraftRevisionsSuperseded(proposalGroupId, exceptRevisionId, Instant.now());
  }

  @Override
  @Transactional
  public List<Long> markReadyExtensionRevisionsSuperseded(long proposalGroupId, long exceptRevisionId) {
    return mapper.markReadyExtensionRevisionsSuperseded(proposalGroupId, exceptRevisionId, Instant.now());
  }

  private LearningPlanProposalGroupRow toGroupRow(LearningPlanProposalGroup group) {
    return new LearningPlanProposalGroupRow(
        group.id(),
        group.userId(),
        group.proposalType().name(),
        group.targetType().name(),
        group.targetId(),
        group.status().name(),
        group.initialInstruction(),
        group.latestProposalId(),
        group.createdAt(),
        group.updatedAt());
  }

  private LearningPlanProposalGroupRow lockAndValidateGroup(
      long groupId,
      long userId,
      LearningPlanProposalType proposalType,
      LearningPlanProposalTargetType targetType,
      long targetId) {
    LearningPlanProposalGroupRow group = mapper.lockProposalGroupForUpdate(groupId, userId);
    if (group == null) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND", "学习计划提案组不存在。");
    }
    if (!proposalType.name().equals(group.proposalType())
        || !targetType.name().equals(group.targetType())
        || group.targetId() == null
        || group.targetId().longValue() != targetId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_INVALID", "学习计划提案组与修订目标不匹配。");
    }
    return group;
  }

  private int nextRevisionNoAfterLock(long proposalGroupId) {
    return Math.max(mapper.nextDraftRevisionNo(proposalGroupId), mapper.nextExtensionRevisionNo(proposalGroupId));
  }

  private void validateExistingDraftRevision(
      LearningPlanDraftRevisionRow existing,
      LearningPlanDraftRevision revision) {
    if (existing == null
        || !existing.proposalGroupId().equals(revision.proposalGroupId())
        || !existing.draftId().equals(revision.draftId())
        || !existing.userId().equals(revision.userId())) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_INVALID", "学习计划草案修订记录与请求不匹配。");
    }
  }

  private void validateExistingExtensionRevision(
      LearningPlanExtensionRevisionRow existing,
      LearningPlanExtensionRevision revision) {
    if (existing == null
        || !existing.proposalGroupId().equals(revision.proposalGroupId())
        || !existing.planId().equals(revision.planId())
        || !existing.userId().equals(revision.userId())) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_INVALID", "学习计划扩展修订记录与请求不匹配。");
    }
  }

  private LearningPlanDraftRevision withRevisionNo(LearningPlanDraftRevision revision, int revisionNo) {
    return new LearningPlanDraftRevision(
        revision.id(),
        revision.proposalGroupId(),
        revision.draftId(),
        revision.userId(),
        revisionNo,
        revision.status(),
        revision.instruction(),
        revision.basePlan(),
        revision.proposedPlan(),
        revision.errorCode(),
        revision.errorMessage(),
        revision.createdAt(),
        revision.updatedAt());
  }

  private LearningPlanExtensionRevision withRevisionNo(LearningPlanExtensionRevision revision, int revisionNo) {
    return new LearningPlanExtensionRevision(
        revision.id(),
        revision.proposalGroupId(),
        revision.planId(),
        revision.userId(),
        revisionNo,
        revision.status(),
        revision.instruction(),
        revision.basePlan(),
        revision.progressSnapshot(),
        revision.baseMaxPhaseIndex(),
        revision.previousExtension(),
        revision.proposedExtension(),
        revision.appliedAt(),
        revision.errorCode(),
        revision.errorMessage(),
        revision.createdAt(),
        revision.updatedAt());
  }

  private LearningPlanDraftRevisionRow toDraftRevisionRow(LearningPlanDraftRevision revision) {
    return new LearningPlanDraftRevisionRow(
        revision.id(),
        revision.proposalGroupId(),
        revision.draftId(),
        revision.userId(),
        revision.revisionNo(),
        revision.status().name(),
        revision.instruction(),
        json(revision.basePlan()),
        json(revision.proposedPlan()),
        revision.errorCode(),
        revision.errorMessage(),
        revision.createdAt(),
        revision.updatedAt());
  }

  private LearningPlanExtensionRevisionRow toExtensionRevisionRow(LearningPlanExtensionRevision revision) {
    return new LearningPlanExtensionRevisionRow(
        revision.id(),
        revision.proposalGroupId(),
        revision.planId(),
        revision.userId(),
        revision.revisionNo(),
        revision.status().name(),
        revision.instruction(),
        json(revision.basePlan()),
        json(revision.progressSnapshot()),
        revision.baseMaxPhaseIndex(),
        json(revision.previousExtension()),
        json(revision.proposedExtension()),
        revision.appliedAt(),
        revision.errorCode(),
        revision.errorMessage(),
        revision.createdAt(),
        revision.updatedAt());
  }

  private LearningPlanProposalGroup toGroup(LearningPlanProposalGroupRow row) {
    return new LearningPlanProposalGroup(
        row.id(),
        row.userId(),
        LearningPlanProposalType.valueOf(row.proposalType()),
        LearningPlanProposalTargetType.valueOf(row.targetType()),
        row.targetId(),
        LearningPlanProposalGroupStatus.valueOf(row.status()),
        row.initialInstruction(),
        row.latestProposalId(),
        row.createdAt(),
        row.updatedAt());
  }

  private LearningPlanDraftRevision toDraftRevision(LearningPlanDraftRevisionRow row) {
    return new LearningPlanDraftRevision(
        row.id(),
        row.proposalGroupId(),
        row.draftId(),
        row.userId(),
        row.revisionNo(),
        LearningPlanProposalRevisionStatus.valueOf(row.status()),
        row.instruction(),
        readNullable(row.basePlanJson(), LearningPlanDraftPlan.class),
        readNullable(row.proposedPlanJson(), LearningPlanDraftPlan.class),
        row.errorCode(),
        row.errorMessage(),
        row.createdAt(),
        row.updatedAt());
  }

  private LearningPlanExtensionRevision toExtensionRevision(LearningPlanExtensionRevisionRow row) {
    return new LearningPlanExtensionRevision(
        row.id(),
        row.proposalGroupId(),
        row.planId(),
        row.userId(),
        row.revisionNo(),
        LearningPlanProposalRevisionStatus.valueOf(row.status()),
        row.instruction(),
        read(row.basePlanJson(), LearningPlanDraftPlan.class),
        read(row.progressSnapshotJson(), MAP_TYPE),
        row.baseMaxPhaseIndex(),
        readNullable(row.previousExtensionJson(), LearningPlanExtensionDraft.class),
        readNullable(row.proposedExtensionJson(), LearningPlanExtensionDraft.class),
        row.appliedAt(),
        row.errorCode(),
        row.errorMessage(),
        row.createdAt(),
        row.updatedAt());
  }

  private JsonNode json(Object value) {
    return value == null ? null : objectMapper.valueToTree(value);
  }

  private <T> T read(JsonNode node, Class<T> type) {
    try {
      return objectMapper.treeToValue(node, type);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_JSON_INVALID", "学习计划 JSON 解析失败。");
    }
  }

  private <T> T readNullable(JsonNode node, Class<T> type) {
    return node == null || node.isNull() ? null : read(node, type);
  }

  private <T> T read(JsonNode node, TypeReference<T> type) {
    try {
      return objectMapper.readerFor(type).readValue(node);
    } catch (IOException exception) {
      throw new LearningPlanException("LEARNING_PLAN_JSON_INVALID", "学习计划 JSON 解析失败。");
    }
  }
}
