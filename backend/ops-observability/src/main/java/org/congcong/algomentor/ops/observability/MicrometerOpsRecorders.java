package org.congcong.algomentor.ops.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.WeakHashMap;

public final class MicrometerOpsRecorders {

  private static final Map<MeterRegistry, ConcurrentMap<SseStreamType, AtomicInteger>> SSE_ACTIVE_CONNECTIONS =
      Collections.synchronizedMap(new WeakHashMap<>());

  private MicrometerOpsRecorders() {
  }

  public static SseOpsRecorder sse(MeterRegistry registry) {
    Objects.requireNonNull(registry, "registry must not be null");
    return new MicrometerSseOpsRecorder(registry, sharedActiveConnections(registry));
  }

  public static AgentOpsRecorder agent(MeterRegistry registry) {
    return new MicrometerAgentOpsRecorder(registry);
  }

  public static LearningOpsRecorder learning(MeterRegistry registry) {
    return new MicrometerLearningOpsRecorder(registry);
  }

  private static ConcurrentMap<SseStreamType, AtomicInteger> sharedActiveConnections(MeterRegistry registry) {
    synchronized (SSE_ACTIVE_CONNECTIONS) {
      return SSE_ACTIVE_CONNECTIONS.computeIfAbsent(registry, ignored -> new ConcurrentHashMap<>());
    }
  }

  private static final class MicrometerSseOpsRecorder implements SseOpsRecorder {

    private final MeterRegistry registry;
    private final ConcurrentMap<SseStreamType, AtomicInteger> activeConnections;

    private MicrometerSseOpsRecorder(
        MeterRegistry registry,
        ConcurrentMap<SseStreamType, AtomicInteger> activeConnections) {
      this.registry = Objects.requireNonNull(registry, "registry must not be null");
      this.activeConnections = Objects.requireNonNull(
          activeConnections,
          "activeConnections must not be null");
    }

    @Override
    public void opened(SseStreamType streamType) {
      streamType = requireStreamType(streamType);
      activeGauge(streamType).incrementAndGet();
      Counter.builder(OpsMetricNames.SSE_CONNECTIONS_OPENED)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .register(registry)
          .increment();
    }

    @Override
    public void completed(SseStreamType streamType) {
      streamType = requireStreamType(streamType);
      decrementActive(streamType);
      Counter.builder(OpsMetricNames.SSE_CONNECTIONS_COMPLETED)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .register(registry)
          .increment();
    }

    @Override
    public void failed(SseStreamType streamType, SseFailureType failureType) {
      streamType = requireStreamType(streamType);
      failureType = Objects.requireNonNull(failureType, "failureType must not be null");
      decrementActive(streamType);
      Counter.builder(OpsMetricNames.SSE_CONNECTIONS_FAILED)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .tag(OpsMetricTags.FAILURE_TYPE, failureType.tagValue())
          .register(registry)
          .increment();
    }

    @Override
    public void timeout(SseStreamType streamType) {
      streamType = requireStreamType(streamType);
      Counter.builder(OpsMetricNames.SSE_CONNECTIONS_TIMEOUT)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .register(registry)
          .increment();
    }

    @Override
    public void clientDisconnected(SseStreamType streamType) {
      streamType = requireStreamType(streamType);
      Counter.builder(OpsMetricNames.SSE_CONNECTIONS_CLIENT_DISCONNECTED)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .register(registry)
          .increment();
    }

    private AtomicInteger activeGauge(SseStreamType streamType) {
      return activeConnections.computeIfAbsent(streamType, this::registerActiveGauge);
    }

    private AtomicInteger registerActiveGauge(SseStreamType streamType) {
      AtomicInteger active = new AtomicInteger();
      Gauge.builder(OpsMetricNames.SSE_CONNECTIONS_ACTIVE, active, AtomicInteger::get)
          .tag(OpsMetricTags.STREAM_TYPE, streamType.tagValue())
          .register(registry);
      return active;
    }

    private void decrementActive(SseStreamType streamType) {
      activeGauge(streamType).updateAndGet(value -> Math.max(0, value - 1));
    }

    private SseStreamType requireStreamType(SseStreamType streamType) {
      return Objects.requireNonNull(streamType, "streamType must not be null");
    }

  }

  private static final class MicrometerAgentOpsRecorder implements AgentOpsRecorder {

    private final MeterRegistry registry;

    private MicrometerAgentOpsRecorder(MeterRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void runStarted(AgentOpsSource source) {
      recordRun(source, OpsStatus.STARTED);
    }

    @Override
    public void runCompleted(AgentOpsSource source) {
      recordRun(source, OpsStatus.COMPLETED);
    }

    @Override
    public void runFailed(AgentOpsSource source) {
      recordRun(source, OpsStatus.FAILED);
    }

    @Override
    public void toolPermissionDecision(String decision) {
      Counter.builder(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS)
          .tag(OpsMetricTags.DECISION, normalizeDecision(decision))
          .register(registry)
          .increment();
    }

    @Override
    public void toolExecution(String toolName, OpsStatus status) {
      Counter.builder(OpsMetricNames.AGENT_TOOL_EXECUTIONS)
          .tag(OpsMetricTags.TOOL_NAME, requireNonBlank(toolName, "toolName"))
          .tag(OpsMetricTags.STATUS, requireStatus(status).tagValue())
          .register(registry)
          .increment();
    }

    private void recordRun(AgentOpsSource source, OpsStatus status) {
      Counter.builder(OpsMetricNames.AGENT_RUNS)
          .tag(OpsMetricTags.SOURCE, requireSource(source).tagValue())
          .tag(OpsMetricTags.STATUS, status.tagValue())
          .register(registry)
          .increment();
    }

    private AgentOpsSource requireSource(AgentOpsSource source) {
      return Objects.requireNonNull(source, "source must not be null");
    }

  }

  private static final class MicrometerLearningOpsRecorder implements LearningOpsRecorder {

    private final MeterRegistry registry;

    private MicrometerLearningOpsRecorder(MeterRegistry registry) {
      this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public void learningPlanDraft(OpsStatus status) {
      recordStatus(OpsMetricNames.LEARNING_PLAN_DRAFT_GENERATIONS, status);
    }

    @Override
    public void practiceMessageStream(OpsStatus status) {
      recordStatus(OpsMetricNames.PRACTICE_MESSAGE_STREAMS, status);
    }

    @Override
    public void practiceCodeReview(OpsStatus status) {
      recordStatus(OpsMetricNames.PRACTICE_CODE_REVIEWS, status);
    }

    private void recordStatus(String metricName, OpsStatus status) {
      Counter.builder(metricName)
          .tag(OpsMetricTags.STATUS, requireStatus(status).tagValue())
          .register(registry)
          .increment();
    }

  }

  private static OpsStatus requireStatus(OpsStatus status) {
    return Objects.requireNonNull(status, "status must not be null");
  }

  private static String normalizeDecision(String decision) {
    if (decision == null || decision.isBlank()) {
      return "unknown";
    }

    return switch (decision.trim().toLowerCase(Locale.ROOT)) {
      case "allow" -> "allow";
      case "deny" -> "deny";
      case "timeout" -> "timeout";
      case "cancelled", "canceled" -> "cancelled";
      default -> "unknown";
    };
  }

  private static String requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

}
