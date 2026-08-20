package org.congcong.algomentor.mentor.application.learningplan.stream;

/** 已经脱敏、可投影到首次草案实时通道的领域事件。 */
public sealed interface LearningPlanDraftGenerationEvent
    permits LearningPlanDraftGenerationEvent.WorkStarted,
        LearningPlanDraftGenerationEvent.WorkProgress,
        LearningPlanDraftGenerationEvent.WorkToolStarted,
        LearningPlanDraftGenerationEvent.WorkToolEnded,
        LearningPlanDraftGenerationEvent.Completed,
        LearningPlanDraftGenerationEvent.Failed {

  record WorkStarted(String message) implements LearningPlanDraftGenerationEvent {
  }

  record WorkProgress(String message) implements LearningPlanDraftGenerationEvent {
  }

  record WorkToolStarted(String toolName) implements LearningPlanDraftGenerationEvent {
  }

  record WorkToolEnded(String toolName) implements LearningPlanDraftGenerationEvent {
  }

  record Completed() implements LearningPlanDraftGenerationEvent {
  }

  record Failed(String code) implements LearningPlanDraftGenerationEvent {
  }
}
