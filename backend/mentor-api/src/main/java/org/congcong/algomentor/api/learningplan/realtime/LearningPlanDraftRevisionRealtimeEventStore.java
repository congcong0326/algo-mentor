package org.congcong.algomentor.api.learningplan.realtime;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEventPublisher;

/** 草案修订短期公开事件日志端口；业务终态始终以 PostgreSQL 为准。 */
public interface LearningPlanDraftRevisionRealtimeEventStore extends LearningPlanDraftRevisionGenerationEventPublisher {

  List<LearningPlanDraftRevisionRealtimeEvent> readAfter(long draftId, long revisionId, String after, boolean block);

  default boolean available() {
    return true;
  }
}
