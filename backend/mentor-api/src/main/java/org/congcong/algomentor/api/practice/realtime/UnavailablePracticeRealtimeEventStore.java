package org.congcong.algomentor.api.practice.realtime;

import java.util.List;
import org.congcong.algomentor.agent.core.AgentStreamEvent;

/** Redis Streams 被显式关闭时的实时日志降级实现。 */
public final class UnavailablePracticeRealtimeEventStore implements PracticeRealtimeEventStore {

  @Override
  public void append(String runUuid, AgentStreamEvent event) {
    // 实时日志关闭不能影响 Agent run。
  }

  @Override
  public List<PracticeRealtimeEvent> readAfter(String runUuid, String after, boolean block) {
    throw new PracticeRealtimeUnavailableException("Practice realtime event store is disabled");
  }

  @Override
  public boolean available() {
    return false;
  }
}
