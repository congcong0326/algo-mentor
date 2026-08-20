package org.congcong.algomentor.api.learningplan.realtime;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;

/** Redis Stream 显式关闭时的降级实现，生成与草案查询仍然可用。 */
public final class UnavailableLearningPlanGenerationRealtimeEventStore
    implements LearningPlanGenerationRealtimeEventStore {

  @Override
  public void append(long draftId, LearningPlanDraftGenerationEvent event) {
    // Redis 是尽力而为的实时日志，关闭时不影响 Agent 数据面。
  }

  @Override
  public List<LearningPlanGenerationRealtimeEvent> readAfter(long draftId, String after, boolean block) {
    throw new LearningPlanGenerationRealtimeUnavailableException(
        "Learning plan generation realtime event store is disabled");
  }

  @Override
  public boolean available() {
    return false;
  }
}
