package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 将 Code Review 更新工具的一次返回收敛为低基数指标。 */
final class LearnerMemoryReviewToolMetrics {

  private final LearnerMemoryMetrics metrics;

  LearnerMemoryReviewToolMetrics(LearnerMemoryMetrics metrics) {
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  JsonNode record(String tool, JsonNode result) {
    return record("REVIEW_UPDATE", tool, result);
  }

  JsonNode record(String purpose, String tool, JsonNode result) {
    String status = result == null ? LearnerMemoryAgentToolContracts.STATUS_FAILED
        : result.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText(
            LearnerMemoryAgentToolContracts.STATUS_FAILED);
    metrics.recordToolCall(
        purpose,
        tool,
        LearnerMemoryAgentToolContracts.STATUS_OK.equals(status) ? "SUCCEEDED" : "FAILED");
    return result;
  }
}
