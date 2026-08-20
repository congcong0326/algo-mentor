package org.congcong.algomentor.api.learningplan.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanActivationRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanContractStateRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanDraftRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanProgressSummaryRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanRow;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivation;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractState;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractStateRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisLearningPlanRepository
    implements LearningPlanDraftRepository, LearningPlanRepository, LearningPlanContractStateRepository,
        LearningPlanActivationRepository {

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
    long draftId = row.id() == null ? 0 : row.id();
    if (draft.id() == null) {
      draftId = mapper.insertDraft(row);
    } else {
      mapper.updateDraft(row);
    }
    return toDraft(mapper.findDraftByIdForUser(draftId, draft.userId()));
  }

  @Override
  @Transactional
  public Optional<LearningPlanDraft> createWithinDailyLimit(
      LearningPlanDraft draft,
      LocalDate quotaDate,
      int dailyLimit,
      Instant consumedAt
  ) {
    if (dailyLimit < 1) {
      return Optional.empty();
    }
    Integer consumedCount = mapper.tryConsumeDailyDraftQuota(
        draft.userId(), quotaDate, dailyLimit, consumedAt);
    if (consumedCount == null) {
      return Optional.empty();
    }
    return Optional.of(save(draft));
  }

  @Override
  public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
    return Optional.ofNullable(mapper.findDraftByIdForUser(draftId, userId)).map(this::toDraft);
  }

  @Override
  public Optional<LearningPlanDraft> findDraftByGenerationRequestKey(long userId, String requestKey) {
    return Optional.ofNullable(mapper.findDraftByGenerationRequestKey(userId, requestKey)).map(this::toDraft);
  }

  @Override
  public void lockGenerationRequest(long userId, String requestKey) {
    mapper.lockGenerationRequest(userId, requestKey);
  }

  @Override
  @Transactional
  public Optional<LearningPlanDraft> completeGeneration(
      LearningPlanDraft draft,
      LearningPlanDraftPlan plan,
      Instant completedAt
  ) {
    LearningPlanDraft next = draft.withGenerationSucceeded(plan, completedAt);
    return mapper.completeGeneratingDraft(toDraftRow(next)) == 1
        ? Optional.of(next)
        : Optional.empty();
  }

  @Override
  @Transactional
  public Optional<LearningPlanDraft> failGeneration(
      LearningPlanDraft draft,
      String code,
      String message,
      Instant completedAt
  ) {
    LearningPlanDraft next = draft.withGenerationFailed(code, message, completedAt);
    return mapper.failGeneratingDraft(toDraftRow(next)) == 1
        ? Optional.of(next)
        : Optional.empty();
  }

  @Override
  @Transactional
  public List<Long> failInterruptedGenerations(
      Instant startedBefore,
      String code,
      String message,
      Instant completedAt
  ) {
    return mapper.failInterruptedGeneratingDrafts(startedBefore, code, message, completedAt);
  }

  @Override
  public Optional<LearningPlanDraft> findDraftByIdForUserForUpdate(long draftId, long userId) {
    return Optional.ofNullable(mapper.findDraftByIdForUserForUpdate(draftId, userId)).map(this::toDraft);
  }

  @Override
  @Transactional
  public int deleteExpiredDrafts(Instant expiredBefore, int limit) {
    List<Long> draftIds = mapper.findExpiredDraftIdsForCleanup(expiredBefore, limit);
    if (draftIds.isEmpty()) {
      return 0;
    }
    mapper.deleteProposalGroupsForDrafts(draftIds);
    return mapper.deleteDraftsByIds(draftIds);
  }

  @Override
  @Transactional
  public int deleteDailyDraftUsageBefore(LocalDate quotaDate, int limit) {
    return mapper.deleteDailyDraftUsageBefore(quotaDate, limit);
  }

  @Override
  @Transactional
  public LearningPlan save(LearningPlan plan) {
    LearningPlanRow row = toPlanRow(plan);
    long planId = row.id() == null ? 0 : row.id();
    if (plan.id() == null) {
      planId = mapper.insertPlan(row);
    } else {
      mapper.updatePlanSnapshot(row);
    }
    replacePlanDetails(planId, plan.plan());
    return toPlan(mapper.findPlanByIdForUser(planId, plan.userId()));
  }

  @Override
  @Transactional
  public Optional<LearningPlan> createIfBelowLimit(LearningPlan plan, int maxSavedPlans) {
    if (maxSavedPlans < 1) {
      return Optional.empty();
    }
    mapper.lockPlanCreationForUser(plan.userId());
    if (mapper.countPlansByUserId(plan.userId()) >= maxSavedPlans) {
      return Optional.empty();
    }
    return Optional.of(save(plan));
  }

  @Override
  public List<LearningPlan> findByUserId(long userId) {
    return mapper.findPlansByUserId(userId).stream().map(this::toPlan).toList();
  }

  @Override
  public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
    int offset = (page - 1) * pageSize;
    List<LearningPlan> items = mapper.findPlansByUserIdPage(userId, pageSize, offset).stream()
        .map(this::toPlan)
        .toList();
    Map<Long, LearningPlanProgressSummary> progressSummaries = progressSummaries(userId, items);
    return new LearningPlanPage(
        items,
        mapper.countPlansByUserId(userId),
        page,
        pageSize,
        mapper.countPlansByUserIdAndStatus(userId, LearningPlanStatus.ACTIVE.name()),
        mapper.countPlansByUserIdAndStatus(userId, LearningPlanStatus.ARCHIVED.name()),
        mapper.findLatestPlanCreatedAtByUserId(userId),
        progressSummaries);
  }

  private Map<Long, LearningPlanProgressSummary> progressSummaries(long userId, List<LearningPlan> plans) {
    if (plans.isEmpty()) {
      return Map.of();
    }
    List<Long> planIds = plans.stream().map(LearningPlan::id).toList();
    Map<Long, LearningPlanProgressSummary> summaries = new HashMap<>();
    for (LearningPlanProgressSummaryRow row : mapper.findProgressSummariesByPlanIds(userId, planIds)) {
      summaries.put(
          row.planId(),
          LearningPlanProgressSummary.fromCounts(row.totalProblemCount(), row.completedProblemCount()));
    }
    return Map.copyOf(summaries);
  }

  @Override
  public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
    return Optional.ofNullable(mapper.findPlanByIdForUser(planId, userId)).map(this::toPlan);
  }

  @Override
  public Optional<LearningPlan> findPlanByIdForUserForUpdate(long planId, long userId) {
    return Optional.ofNullable(mapper.findPlanByIdForUserForUpdate(planId, userId)).map(this::toPlan);
  }

  @Override
  public Optional<LearningPlanContractState> findByPlan(long userId, long planId) {
    return Optional.ofNullable(mapper.findContractStateByPlan(userId, planId)).map(this::toContractState);
  }

  @Override
  public Optional<LearningPlanActivation> findSelectionByUserId(long userId) {
    return Optional.ofNullable(mapper.findActiveSelectionByUserId(userId)).map(this::toActivation);
  }

  @Override
  @Transactional
  public LearningPlanActivation upsert(long userId, long planId, Instant activatedAt) {
    return toActivation(mapper.upsertActiveSelection(userId, planId, activatedAt));
  }

  @Override
  @Transactional
  public LearningPlanContractState pause(long userId, long planId, LocalDate frozenEstimatedCompletionDate) {
    return toContractState(mapper.pauseContractState(userId, planId, frozenEstimatedCompletionDate));
  }

  @Override
  @Transactional
  public LearningPlanContractState resume(long userId, long planId, Instant noticeAt) {
    return toContractState(mapper.resumeContractState(userId, planId, noticeAt));
  }

  @Override
  @Transactional
  public LearningPlanContractState closeOut(long userId, long planId, LocalDate frozenEstimatedCompletionDate) {
    return toContractState(mapper.closeOutContractState(userId, planId, frozenEstimatedCompletionDate));
  }

  @Override
  public void clearConfirmedPlanReferences(long userId, long planId) {
    mapper.clearConfirmedPlanReferences(userId, planId);
  }

  @Override
  @Transactional
  public boolean deletePlanByIdForUser(long planId, long userId) {
    return mapper.deletePlanByIdForUser(planId, userId) > 0;
  }

  @Override
  @Transactional
  public boolean deletePlanAndClearReferences(long userId, long planId) {
    mapper.clearConfirmedPlanReferences(userId, planId);
    return mapper.deletePlanByIdForUser(planId, userId) > 0;
  }

  @Override
  @Transactional
  public LearningPlan appendPhases(long userId, long planId, List<LearningPlanPhaseDraft> newPhases) {
    LearningPlan current = Optional.ofNullable(mapper.findPlanByIdForUserForUpdate(planId, userId))
        .map(this::toPlan)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
    int baseMaxPhaseIndex = mapper.findMaxPhaseIndex(planId);
    List<LearningPlanPhaseDraft> reindexedNewPhases = reindexPhases(
        newPhases == null ? List.of() : newPhases,
        baseMaxPhaseIndex);
    List<LearningPlanPhaseDraft> phases = new ArrayList<>(current.plan().phases());
    phases.addAll(reindexedNewPhases);
    LearningPlanDraftPlan mergedPlan = new LearningPlanDraftPlan(
        current.plan().title(),
        current.plan().summary(),
        current.plan().intent(),
        current.plan().objective(),
        current.plan().durationWeeks(),
        current.plan().level(),
        current.plan().weeklyHours(),
        current.plan().programmingLanguage(),
        current.plan().difficultyDistribution(),
        current.plan().topicPreferences(),
        current.plan().additionalConstraints(),
        phases,
        current.plan().metadata());

    for (LearningPlanPhaseDraft phase : reindexedNewPhases) {
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
    mapper.updatePlanJsonSnapshot(planId, userId, mergedPlan.title(), json(mergedPlan), Instant.now());
    return toPlan(mapper.findPlanByIdForUser(planId, userId));
  }

  private List<LearningPlanPhaseDraft> reindexPhases(List<LearningPlanPhaseDraft> phases, int baseMaxPhaseIndex) {
    List<LearningPlanPhaseDraft> reindexed = new ArrayList<>(phases.size());
    int nextPhaseIndex = baseMaxPhaseIndex + 1;
    for (LearningPlanPhaseDraft phase : phases) {
      reindexed.add(new LearningPlanPhaseDraft(
          nextPhaseIndex++,
          phase.title(),
          phase.durationWeeks(),
          phase.focus(),
          phase.problems()));
    }
    return reindexed;
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
        draft.source().name(),
        draft.status().name(),
        json(draft.brief()),
        json(draft.messages()),
        json(draft.missingFields()),
        draft.assistantMessage(),
        json(draft.draftPlan()),
        draft.confirmedPlanId(),
        draft.expiresAt(),
        draft.createdAt(),
        draft.updatedAt(),
        draft.generationRequestKey(),
        draft.generationRequestFingerprint(),
        draft.generationRunId(),
        draft.generationErrorCode(),
        draft.generationErrorMessage(),
        draft.generationStartedAt(),
        draft.generationCompletedAt());
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
        LearningPlanDraftSource.valueOf(row.draftSource()),
        LearningPlanDraftStatus.valueOf(row.status()),
        read(row.commandJson(), LearningPlanBrief.class),
        read(row.messagesJson(), STRING_LIST),
        read(row.missingFieldsJson(), STRING_LIST),
        row.assistantMessage(),
        readNullable(row.draftPlanJson(), LearningPlanDraftPlan.class),
        row.confirmedPlanId(),
        row.expiresAt(),
        row.createdAt(),
        row.updatedAt(),
        row.generationRequestKey(),
        row.generationRequestFingerprint(),
        row.generationRunId(),
        row.generationErrorCode(),
        row.generationErrorMessage(),
        row.generationStartedAt(),
        row.generationCompletedAt());
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

  private LearningPlanContractState toContractState(LearningPlanContractStateRow row) {
    return new LearningPlanContractState(
        row.userId(),
        row.planId(),
        row.paused(),
        row.closedOut(),
        row.frozenEstimatedCompletionDate(),
        row.lastRebalanceNoticeAt(),
        row.createdAt(),
        row.updatedAt());
  }

  private LearningPlanActivation toActivation(LearningPlanActivationRow row) {
    return new LearningPlanActivation(
        row.userId(),
        row.planId(),
        row.activatedAt(),
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
