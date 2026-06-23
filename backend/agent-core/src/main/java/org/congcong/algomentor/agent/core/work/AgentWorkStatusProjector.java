package org.congcong.algomentor.agent.core.work;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/**
 * 把调试型 Agent 事件投影为普通用户可见的简短工作状态。
 */
public class AgentWorkStatusProjector {

  private final AgentWorkStatusProfile profile;
  private final Clock clock;
  private final StringBuilder previewBuffer = new StringBuilder();
  private Instant lastProgressAt = Instant.EPOCH;
  private String runId = "unknown";

  public AgentWorkStatusProjector(AgentWorkStatusProfile profile, Clock clock) {
    this.profile = profile;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public Optional<AgentWorkStatusEvent> project(AgentStreamEvent event) {
    if (event instanceof AgentStreamEvent.AgentRunStart start) {
      runId = start.runId();
      return Optional.of(new AgentWorkStatusEvent.WorkStart(
          start.runId(),
          profile.scenario(),
          profile.startMessage()));
    }
    if (event instanceof AgentStreamEvent.Llm llm && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
      return progress(delta.content());
    }
    if (event instanceof AgentStreamEvent.AgentToolStart start) {
      return Optional.of(new AgentWorkStatusEvent.WorkToolStart(
          start.runId(),
          profile.scenario(),
          start.toolName(),
          toolMessage(start.toolName(), false)));
    }
    if (event instanceof AgentStreamEvent.AgentToolEnd end) {
      return Optional.of(new AgentWorkStatusEvent.WorkToolEnd(
          end.runId(),
          profile.scenario(),
          end.toolName(),
          toolMessage(end.toolName(), true)));
    }
    if (event instanceof AgentStreamEvent.AgentRunEnd end && profile.emitDone()) {
      return Optional.of(new AgentWorkStatusEvent.WorkDone(end.runId(), profile.scenario(), "生成完成"));
    }
    if (event instanceof AgentStreamEvent.AgentError error) {
      return Optional.of(new AgentWorkStatusEvent.WorkError(
          error.runId(),
          profile.scenario(),
          error.error().code().name(),
          "处理失败，请稍后重试。",
          error.error().retryable()));
    }
    return Optional.empty();
  }

  private Optional<AgentWorkStatusEvent> progress(String delta) {
    if (delta == null || delta.isBlank()) {
      return Optional.empty();
    }
    previewBuffer.append(delta);
    Instant now = clock.instant();
    if (now.isBefore(lastProgressAt.plus(profile.progressThrottle()))) {
      return Optional.empty();
    }
    lastProgressAt = now;
    String preview = preview(previewBuffer.toString());
    return Optional.of(new AgentWorkStatusEvent.WorkProgress(
        runId,
        profile.scenario(),
        profile.progressPrefix() + "：" + preview,
        preview));
  }

  private String toolMessage(String toolName, boolean finished) {
    String label = profile.toolLabels().get(toolName);
    if (label == null || label.isBlank()) {
      return finished ? "工具执行完成" : "正在执行工具";
    }
    if (finished && label.startsWith("正在")) {
      return label.substring("正在".length()) + "完成";
    }
    return finished ? label + "完成" : label;
  }

  private String normalize(String text) {
    return text.replaceAll("\\s+", " ").trim();
  }

  private String preview(String text) {
    String normalized = normalize(text);
    if (looksLikeStructuredJson(normalized)) {
      return "整理结构化结果";
    }
    return truncate(normalized, profile.previewMaxChars());
  }

  private boolean looksLikeStructuredJson(String text) {
    return text.startsWith("{") || text.startsWith("[") || text.startsWith("\"{") || text.startsWith("\"[");
  }

  private String truncate(String text, int maxChars) {
    if (text.length() <= maxChars) {
      return text;
    }
    return text.substring(0, Math.max(1, maxChars - 3)) + "...";
  }
}
