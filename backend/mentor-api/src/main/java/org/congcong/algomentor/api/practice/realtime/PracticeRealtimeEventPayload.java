package org.congcong.algomentor.api.practice.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** Practice Chat 对浏览器公开的单个实时事件，不携带 Agent 内部 metadata。 */
public record PracticeRealtimeEventPayload(String eventName, JsonNode data) {

  public PracticeRealtimeEventPayload {
    if (eventName == null || eventName.isBlank()) {
      throw new IllegalArgumentException("Practice realtime event name must not be blank");
    }
    if (data == null || !data.isObject()) {
      throw new IllegalArgumentException("Practice realtime event data must be an object");
    }
  }
}
