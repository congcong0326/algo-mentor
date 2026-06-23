package org.congcong.algomentor.mentor.application.learningplan.stream;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;

/**
 * 学习计划流式接口中的业务结果事件。
 */
public sealed interface LearningPlanDraftEvent
    permits LearningPlanDraftEvent.DraftReady, LearningPlanDraftEvent.DraftError {

  String eventName();

  record DraftReady(LearningPlanDraftResult draft) implements LearningPlanDraftEvent {
    @Override
    public String eventName() {
      return LearningPlanStreamConstants.DRAFT_READY;
    }
  }

  record DraftError(String code, String message, boolean retryable) implements LearningPlanDraftEvent {
    @Override
    public String eventName() {
      return LearningPlanStreamConstants.DRAFT_ERROR;
    }
  }
}
