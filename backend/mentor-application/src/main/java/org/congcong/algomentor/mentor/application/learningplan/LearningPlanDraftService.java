package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class LearningPlanDraftService {

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanRepository planRepository;
  private final LearningPlanAgentService agentService;
  private final LearningPlanDraftValidator validator;
  private final Clock clock;

  public LearningPlanDraftService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanRepository planRepository,
      LearningPlanAgentService agentService,
      LearningPlanDraftValidator validator,
      Clock clock) {
    this.draftRepository = draftRepository;
    this.planRepository = planRepository;
    this.agentService = agentService;
    this.validator = validator;
    this.clock = clock;
  }

  public LearningPlanDraftResult createDraft(long userId, LearningPlanDraftCommand command) {
    Instant now = clock.instant();
    LearningPlanDraft draft = new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.COLLECTING,
        command,
        List.of(),
        List.of(),
        null,
        null,
        null,
        now.plus(14, ChronoUnit.DAYS),
        now,
        now);
    LearningPlanDraft saved = draftRepository.save(draft);
    return LearningPlanDraftResult.fromDraft(advance(saved));
  }

  public LearningPlanDraftResult continueDraft(long userId, long draftId, String message) {
    LearningPlanDraft draft = draftRepository.findDraftByIdForUser(draftId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
    if (draft.status() == LearningPlanDraftStatus.CONFIRMED) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_CONFIRMED", "学习计划草案已确认保存。");
    }
    List<String> messages = new ArrayList<>(draft.messages());
    String normalizedMessage = message == null ? "" : message.trim();
    if (!normalizedMessage.isEmpty()) {
      messages.add(normalizedMessage);
    }
    LearningPlanDraftCommand command = draft.command();
    if (command.goal() == null && !normalizedMessage.isEmpty()) {
      command = command.withGoal(normalizedMessage);
    }
    LearningPlanDraft updated = draftRepository.save(draft.withCommandAndMessages(command, messages, clock.instant()));
    return LearningPlanDraftResult.fromDraft(advance(updated));
  }

  public LearningPlanConfirmResult confirmDraft(long userId, long draftId) {
    LearningPlanDraft draft = draftRepository.findDraftByIdForUser(draftId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
    if (draft.confirmedPlanId() != null) {
      return new LearningPlanConfirmResult(draft.confirmedPlanId(), draft.draftPlan().title(), LearningPlanStatus.ACTIVE);
    }
    if (draft.status() != LearningPlanDraftStatus.GENERATED || draft.draftPlan() == null) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_GENERATED", "只有已生成的学习计划草案可以确认保存。");
    }
    validator.validateGeneratedPlan(draft.draftPlan());
    Instant now = clock.instant();
    LearningPlan savedPlan = planRepository.save(new LearningPlan(
        null,
        userId,
        LearningPlanStatus.ACTIVE,
        draft.draftPlan(),
        now,
        now));
    draftRepository.save(draft.withConfirmedPlanId(savedPlan.id(), now));
    return new LearningPlanConfirmResult(savedPlan.id(), savedPlan.plan().title(), savedPlan.status());
  }

  private LearningPlanDraft advance(LearningPlanDraft draft) {
    List<String> missingFields = validator.missingRequiredFields(draft.command());
    LearningPlanAgentResult agentResult = agentService.run(draft.command(), missingFields);
    Instant now = clock.instant();
    if (agentResult.action() == LearningPlanAgentAction.ASK_CLARIFICATION) {
      return draftRepository.save(draft.withState(
          LearningPlanDraftStatus.COLLECTING,
          agentResult.missingFields(),
          agentResult.assistantMessage(),
          null,
          now));
    }
    try {
      validator.validateGeneratedPlan(agentResult.draftPlan());
      return draftRepository.save(draft.withState(
          LearningPlanDraftStatus.GENERATED,
          List.of(),
          agentResult.assistantMessage(),
          agentResult.draftPlan(),
          now));
    } catch (LearningPlanException exception) {
      return draftRepository.save(draft.withState(
          LearningPlanDraftStatus.GENERATION_FAILED,
          List.of(),
          exception.getMessage(),
          null,
          now));
    }
  }
}
