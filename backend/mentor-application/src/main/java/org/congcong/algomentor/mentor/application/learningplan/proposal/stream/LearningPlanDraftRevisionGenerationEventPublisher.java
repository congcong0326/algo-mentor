package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

/** 修订公开事件的短期 transport 端口；持久化状态始终以 PostgreSQL 为准。 */
@FunctionalInterface
public interface LearningPlanDraftRevisionGenerationEventPublisher {

  void append(long draftId, long revisionId, LearningPlanDraftRevisionGenerationEvent event);
}
