package org.congcong.algomentor.api.learningplan.realtime;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;

/** Redis 关闭时的降级实现，后台修订和状态查询不受影响。 */
public final class UnavailableLearningPlanDraftRevisionRealtimeEventStore
    implements LearningPlanDraftRevisionRealtimeEventStore {

  @Override
  public void append(long draftId, long revisionId, LearningPlanDraftRevisionGenerationEvent event) {
    // Redis 仅为尽力而为的观察通道。
  }

  @Override
  public List<LearningPlanDraftRevisionRealtimeEvent> readAfter(long draftId, long revisionId, String after, boolean block) {
    throw new LearningPlanGenerationRealtimeUnavailableException(
        "Learning plan draft revision realtime event store is disabled");
  }

  @Override
  public boolean available() {
    return false;
  }
}
