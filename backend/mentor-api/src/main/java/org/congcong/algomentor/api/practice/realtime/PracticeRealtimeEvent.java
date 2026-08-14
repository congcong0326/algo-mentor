package org.congcong.algomentor.api.practice.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** Redis Stream 中可回放的一条 Practice Chat SSE 事件。 */
public record PracticeRealtimeEvent(String cursor, String eventName, JsonNode data) {

  public PracticeRealtimeEvent {
    if (cursor == null || cursor.isBlank()) {
      throw new IllegalArgumentException("Practice realtime cursor must not be blank");
    }
    if (eventName == null || eventName.isBlank()) {
      throw new IllegalArgumentException("Practice realtime event name must not be blank");
    }
    if (data == null || data.isMissingNode()) {
      throw new IllegalArgumentException("Practice realtime event data must not be null");
    }
  }
}
