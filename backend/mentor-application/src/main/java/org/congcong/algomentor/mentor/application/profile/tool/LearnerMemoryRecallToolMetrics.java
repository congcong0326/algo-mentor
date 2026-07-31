package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 将三项 recall 工具的返回结果收敛为一次低基数观测。 */
final class LearnerMemoryRecallToolMetrics {

  private final LearnerMemoryMetrics metrics;

  LearnerMemoryRecallToolMetrics(LearnerMemoryMetrics metrics) {
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  JsonNode record(String tool, JsonNode result) {
    String status = result == null ? LearnerMemoryRecallToolContracts.STATUS_FAILED
        : result.path(LearnerMemoryRecallToolContracts.FIELD_STATUS).asText(
            LearnerMemoryRecallToolContracts.STATUS_FAILED);
    String metricStatus = LearnerMemoryRecallToolContracts.STATUS_OK.equals(status) ? "SUCCEEDED"
        : LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED.equals(status) ? "REJECTED"
            : "FAILED";
    metrics.recordToolCall("RECALL", tool, metricStatus);
    if ("SUCCEEDED".equals(metricStatus) && result != null) {
      metrics.recordRecallToolResultChars(tool, result.toString().length());
    }
    return result;
  }
}
