package org.congcong.algomentor.api.learningplan.realtime;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEventPublisher;

/** 首次草案的短期公开事件日志端口；PostgreSQL 以外不保存业务事实。 */
public interface LearningPlanGenerationRealtimeEventStore extends LearningPlanDraftGenerationEventPublisher {

  List<LearningPlanGenerationRealtimeEvent> readAfter(long draftId, String after, boolean block);

  default boolean available() {
    return true;
  }
}
