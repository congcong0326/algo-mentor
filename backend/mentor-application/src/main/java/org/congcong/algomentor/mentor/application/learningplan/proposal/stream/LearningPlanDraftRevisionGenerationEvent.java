package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

/** 后台修订执行过程中可安全投影至公开实时通道的领域事件。 */
public sealed interface LearningPlanDraftRevisionGenerationEvent {

  record WorkStarted() implements LearningPlanDraftRevisionGenerationEvent {
  }

  record WorkProgress() implements LearningPlanDraftRevisionGenerationEvent {
  }

  record Completed() implements LearningPlanDraftRevisionGenerationEvent {
  }

  record Failed(String code) implements LearningPlanDraftRevisionGenerationEvent {
  }

  record Superseded(String code) implements LearningPlanDraftRevisionGenerationEvent {
  }
}
