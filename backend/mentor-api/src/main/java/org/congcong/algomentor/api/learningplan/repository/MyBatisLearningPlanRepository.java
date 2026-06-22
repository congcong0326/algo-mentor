package org.congcong.algomentor.api.learningplan.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisLearningPlanRepository implements LearningPlanDraftRepository, LearningPlanRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };

  private final LearningPlanMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisLearningPlanRepository(LearningPlanMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public LearningPlanDraft save(LearningPlanDraft draft) {
    LearningPlanDraftRow row = toDraftRow(draft);
    if (draft.id() == null) {
      mapper.insertDraft(row);
    } else {
      mapper.updateDraft(row);
    }
    return toDraft(mapper.findDraftByIdForUser(row.id(), draft.userId()));
  }

  @Override
  public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
    return Optional.ofNullable(mapper.findDraftByIdForUser(draftId, userId)).map(this::toDraft);
  }

  @Override
  @Transactional
  public LearningPlan save(LearningPlan plan) {
    LearningPlanRow row = toPlanRow(plan);
    if (plan.id() == null) {
      mapper.insertPlan(row);
    } else {
      mapper.updatePlanSnapshot(row);
    }
    replacePlanDetails(row.id(), plan.plan());
    return toPlan(mapper.findPlanByIdForUser(row.id(), plan.userId()));
  }

  @Override
  public List<LearningPlan> findByUserId(long userId) {
    return mapper.findPlansByUserId(userId).stream().map(this::toPlan).toList();
  }

  @Override
  public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
    return Optional.ofNullable(mapper.findPlanByIdForUser(planId, userId)).map(this::toPlan);
  }

  private void replacePlanDetails(long planId, LearningPlanDraftPlan plan) {
    mapper.deletePlanPhases(planId);
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      mapper.insertPlanPhase(planId, phase.phaseIndex(), phase.title(), phase.durationWeeks(), phase.focus());
      for (LearningPlanProblemDraft problem : phase.problems()) {
        mapper.insertPlanProblem(
            planId,
            phase.phaseIndex(),
            problem.slug(),
            problem.frontendId(),
            problem.title(),
            problem.titleCn(),
            problem.difficulty(),
            problem.reason(),
            problem.sortOrder());
      }
    }
  }

  private LearningPlanDraftRow toDraftRow(LearningPlanDraft draft) {
    return new LearningPlanDraftRow(
        draft.id(),
        draft.userId(),
        draft.status().name(),
        json(draft.command()),
        json(draft.messages()),
        json(draft.missingFields()),
        draft.assistantMessage(),
        json(draft.draftPlan()),
        draft.confirmedPlanId(),
        draft.expiresAt(),
        draft.createdAt(),
        draft.updatedAt());
  }

  private LearningPlanRow toPlanRow(LearningPlan plan) {
    return new LearningPlanRow(
        plan.id(),
        plan.userId(),
        plan.status().name(),
        plan.plan().title(),
        json(plan.plan()),
        plan.createdAt(),
        plan.updatedAt());
  }

  private LearningPlanDraft toDraft(LearningPlanDraftRow row) {
    return new LearningPlanDraft(
        row.id(),
        row.userId(),
        LearningPlanDraftStatus.valueOf(row.status()),
        read(row.commandJson(), LearningPlanDraftCommand.class),
        read(row.messagesJson(), STRING_LIST),
        read(row.missingFieldsJson(), STRING_LIST),
        row.assistantMessage(),
        readNullable(row.draftPlanJson(), LearningPlanDraftPlan.class),
        row.confirmedPlanId(),
        row.expiresAt(),
        row.createdAt(),
        row.updatedAt());
  }

  private LearningPlan toPlan(LearningPlanRow row) {
    return new LearningPlan(
        row.id(),
        row.userId(),
        LearningPlanStatus.valueOf(row.status()),
        read(row.planJson(), LearningPlanDraftPlan.class),
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
