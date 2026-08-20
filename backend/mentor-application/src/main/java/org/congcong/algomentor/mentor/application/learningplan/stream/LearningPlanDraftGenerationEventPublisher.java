package org.congcong.algomentor.mentor.application.learningplan.stream;

/** 首次草案生成的尽力而为实时事件出口；实现失败不得影响 Agent 或 PostgreSQL 状态。 */
@FunctionalInterface
public interface LearningPlanDraftGenerationEventPublisher {

  void append(long draftId, LearningPlanDraftGenerationEvent event);
}
