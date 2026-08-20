package org.congcong.algomentor.api.learningplan.realtime;

import com.fasterxml.jackson.databind.JsonNode;

/** 写入 Redis envelope 前的最小公开载荷。 */
record LearningPlanGenerationRealtimePayload(String eventName, JsonNode data) {
}
