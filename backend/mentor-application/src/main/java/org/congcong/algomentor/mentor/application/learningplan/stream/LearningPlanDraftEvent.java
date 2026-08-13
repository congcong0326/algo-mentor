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

  /**
   * {@code reason} 仅承载已审核、可面向用户展示的失败原因；不得直接透传异常消息。
   */
  record DraftError(String code, String message, boolean retryable, String reason) implements LearningPlanDraftEvent {
    @Override
    public String eventName() {
      return LearningPlanStreamConstants.DRAFT_ERROR;
    }
  }
}
