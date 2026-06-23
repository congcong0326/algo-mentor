package org.congcong.algomentor.agent.core.work;

import java.time.Duration;
import java.util.Map;

/**
 * Agent 工作状态投影的业务场景配置。
 */
public record AgentWorkStatusProfile(
    String scenario,
    String startMessage,
    String progressPrefix,
    Map<String, String> toolLabels,
    int previewMaxChars,
    Duration progressThrottle,
    boolean emitDone
) {

  public AgentWorkStatusProfile {
    if (scenario == null || scenario.isBlank()) {
      throw new IllegalArgumentException("Agent work status scenario must not be blank");
    }
    startMessage = blankToDefault(startMessage, "开始处理请求");
    progressPrefix = blankToDefault(progressPrefix, "正在处理");
    toolLabels = toolLabels == null ? Map.of() : Map.copyOf(toolLabels);
    previewMaxChars = previewMaxChars < 1 ? 24 : previewMaxChars;
    progressThrottle = progressThrottle == null ? Duration.ofMillis(500) : progressThrottle;
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
