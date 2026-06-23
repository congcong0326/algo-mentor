package org.congcong.algomentor.mentor.application.learningplan.stream;

import org.congcong.algomentor.agent.core.work.AgentWorkStatusEvent;

/**
 * 学习计划流式接口对 API 层发布的事件。
 */
public sealed interface LearningPlanDraftStreamEvent
    permits LearningPlanDraftStreamEvent.Work, LearningPlanDraftStreamEvent.Draft {

  String eventName();

  record Work(AgentWorkStatusEvent event) implements LearningPlanDraftStreamEvent {
    @Override
    public String eventName() {
      return event.eventName();
    }
  }

  record Draft(LearningPlanDraftEvent event) implements LearningPlanDraftStreamEvent {
    @Override
    public String eventName() {
      return event.eventName();
    }
  }
}
