package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** 从 revision Redis Stream 解码后的公开 SSE 事件。 */
public record LearningPlanDraftRevisionRealtimeEvent(String cursor, String eventName, JsonNode data) {
}
