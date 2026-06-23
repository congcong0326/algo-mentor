package org.congcong.algomentor.ai.governance.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public class AiRunMetricsObserver implements AgentLoopObserver {

  private final MeterRegistry registry;
  private final AtomicInteger activeRuns = new AtomicInteger();
  private final Map<String, RunMetricsBuffer> buffers = new ConcurrentHashMap<>();

  public AiRunMetricsObserver(MeterRegistry registry) {
    this.registry = registry;
    Gauge.builder("ai.run.active", activeRuns, AtomicInteger::get)
        .tag("purpose", "ALL")
        .tag("source", "ALL")
        .register(registry);
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    buffers.put(context.runId(), new RunMetricsBuffer(Instant.now()));
    activeRuns.incrementAndGet();
  }

  @Override
  public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    RunMetricsBuffer buffer = buffers.computeIfAbsent(context.runId(), ignored -> new RunMetricsBuffer(Instant.now()));
    if (event instanceof LlmStreamEvent.MessageStart start) {
      buffer.provider = start.provider() == null ? null : start.provider().value();
      buffer.model = start.model() == null ? null : start.model().value();
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      counter("ai.run.tokens", admission, "type", "input").increment(usage.usage().inputTokens());
      counter("ai.run.tokens", admission, "type", "output").increment(usage.usage().outputTokens());
      counter("ai.run.tokens", admission, "type", "cached").increment(usage.usage().cachedTokens());
      counter("ai.run.tokens", admission, "type", "reasoning").increment(usage.usage().reasoningTokens());
      counter("ai.run.tokens", admission, "type", "total").increment(usage.usage().totalTokens());
    }
  }

  @Override
  public void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
    AiRunAdmission admission = admission(context);
    if (admission != null) {
      counter("ai.tool.calls", admission, "tool", toolName(toolCall)).increment();
    }
  }

  @Override
  public void onToolError(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, AgentException error) {
    AiRunAdmission admission = admission(context);
    if (admission != null) {
      counter("ai.tool.errors", admission, "tool", toolName(toolCall)).increment();
    }
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    finish(context, AiRunStatus.COMPLETED, null);
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    String code = error == null ? "AI_UNKNOWN" : error.code().name();
    finish(context, AiRunStatus.FAILED, code);
  }

  public void recordRejection(AiRunAdmission admission, String rejectionCode) {
    counter("ai.run.rejections", admission, "rejection_code", tag(rejectionCode)).increment();
  }

  private void finish(AgentLoopContext context, AiRunStatus status, String errorCode) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    RunMetricsBuffer buffer = buffers.remove(context.runId());
    activeRuns.decrementAndGet();
    String provider = tag(buffer == null ? null : buffer.provider);
    String model = tag(buffer == null ? null : buffer.model);
    Counter.builder("ai.run.requests")
        .tag("purpose", admission.purpose().name())
        .tag("source", admission.source().name())
        .tag("status", status.name())
        .tag("provider", provider)
        .tag("model", model)
        .register(registry)
        .increment();
    Timer.builder("ai.run.duration")
        .tag("purpose", admission.purpose().name())
        .tag("source", admission.source().name())
        .tag("status", status.name())
        .register(registry)
        .record(Duration.between(buffer == null ? Instant.now() : buffer.startedAt, Instant.now()));
    if (errorCode != null) {
      Counter.builder("ai.run.errors")
          .tag("error_code", errorCode)
          .tag("purpose", admission.purpose().name())
          .register(registry)
          .increment();
    }
  }

  private Counter counter(String name, AiRunAdmission admission, String extraTag, String extraValue) {
    return Counter.builder(name)
        .tag("purpose", admission.purpose().name())
        .tag("source", admission.source().name())
        .tag(extraTag, tag(extraValue))
        .register(registry);
  }

  private AiRunAdmission admission(AgentLoopContext context) {
    Object value = context.metadata().get(AiGovernanceMetadataKeys.ADMISSION);
    return value instanceof AiRunAdmission admission ? admission : null;
  }

  private static String toolName(LlmToolCall toolCall) {
    return toolCall == null ? "unknown" : tag(toolCall.name());
  }

  private static String tag(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  private static final class RunMetricsBuffer {
    private final Instant startedAt;
    private String provider;
    private String model;

    private RunMetricsBuffer(Instant startedAt) {
      this.startedAt = startedAt;
    }
  }
}
