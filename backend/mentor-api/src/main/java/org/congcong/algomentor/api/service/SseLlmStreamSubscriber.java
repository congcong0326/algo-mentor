package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.OpsLogEventType;
import org.congcong.algomentor.ops.observability.OpsLogFields;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.SseFailureType;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseLlmStreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(SseLlmStreamSubscriber.class);

  private final SseEmitter emitter;
  private final LlmStreamSseMapper mapper;
  private final boolean cancelUpstreamOnClientDisconnect;
  private final SseStreamType streamType;
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;
  private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);
  private final AtomicBoolean sseTerminalRecorded = new AtomicBoolean(false);
  private final AtomicBoolean practiceBusinessRecorded = new AtomicBoolean(false);
  private final AtomicBoolean clientDisconnectedRecorded = new AtomicBoolean(false);
  private final AtomicBoolean timeoutRecorded = new AtomicBoolean(false);
  private final AtomicBoolean clientConnected = new AtomicBoolean(true);
  private Flow.Subscription subscription;

  public SseLlmStreamSubscriber(SseEmitter emitter, LlmStreamSseMapper mapper) {
    this(emitter, mapper, true);
  }

  public SseLlmStreamSubscriber(
      SseEmitter emitter,
      LlmStreamSseMapper mapper,
      boolean cancelUpstreamOnClientDisconnect
  ) {
    this(
        emitter,
        mapper,
        cancelUpstreamOnClientDisconnect,
        SseStreamType.AGENT_CONVERSATION,
        NoopOpsRecorders.sse(),
        NoopOpsRecorders.learning(),
        new StructuredOpsLogger());
  }

  public SseLlmStreamSubscriber(
      SseEmitter emitter,
      LlmStreamSseMapper mapper,
      boolean cancelUpstreamOnClientDisconnect,
      SseStreamType streamType,
      SseOpsRecorder sseOpsRecorder,
      LearningOpsRecorder learningOpsRecorder,
      StructuredOpsLogger opsLogger
  ) {
    this.emitter = emitter;
    this.mapper = mapper;
    this.cancelUpstreamOnClientDisconnect = cancelUpstreamOnClientDisconnect;
    this.streamType = Objects.requireNonNull(streamType, "streamType must not be null");
    this.sseOpsRecorder = Objects.requireNonNull(sseOpsRecorder, "sseOpsRecorder must not be null");
    this.learningOpsRecorder = Objects.requireNonNull(learningOpsRecorder, "learningOpsRecorder must not be null");
    this.opsLogger = Objects.requireNonNull(opsLogger, "opsLogger must not be null");
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    sseOpsRecorder.opened(streamType);
    opsLogger.info(log, OpsLogEventType.SSE_CONNECTION_OPENED, logFields(null));
    /*
     * Flow 的订阅模型要求 Subscriber 主动向上游声明“我还能处理多少个事件”。
     * 这里每次只 request(1)，表示先要 1 个事件，等 onNext 成功写入 SSE 后再要下一个。
     * 这样可以让上游按 HTTP 写出速度推进，避免一次性把大量 token/工具事件压到内存里。
     */
    subscription.request(1);
  }

  @Override
  public void onNext(AgentStreamEvent event) {
    if (!clientConnected.get()) {
      requestNextOrFinish(event);
      return;
    }
    boolean terminalEvent = isTerminalEvent(event);
    if (terminalEvent) {
      recordTerminalEvent(event);
      terminalEventSent.set(true);
    }
    try {
      /*
       * Publisher 产出的是领域事件 AgentStreamEvent；浏览器需要的是 SSE 协议格式。
       * mapper.toSseEvent(...) 负责把领域事件映射为 event: xxx + data: {...}，
       * emitter.send(...) 则把该事件写到当前 HTTP text/event-stream 响应中。
       */
      emitter.send(mapper.toSseEvent(event));
      if (terminalEvent) {
        /*
         * AgentRunEnd/AgentError 是业务终态事件。发送终态后主动 complete 响应并 cancel 订阅，
         * 防止上游继续推送事件，也让前端能明确感知本次 SSE 流结束。
         */
        emitter.complete();
        cancel();
        return;
      }
      // 当前事件已经成功写给客户端，再向上游申请下一个事件。
      subscription.request(1);
    } catch (IOException | RuntimeException sendFailure) {
      /*
       * 写 HTTP 响应失败通常意味着客户端已刷新、离开页面或网络断开。
       * 对于需要后台持久化最终结果的流，不能把这个客户端事件继续传播成 Agent run cancel；
       * 此时只分离 SSE 写端，并继续 drain 上游事件，避免 backpressure 卡住后台 run。
       */
      clientConnected.set(false);
      recordClientDisconnected(sendFailure);
      if (cancelUpstreamOnClientDisconnect) {
        cancel();
      } else {
        requestNextOrFinish(event);
      }
      emitter.completeWithError(sendFailure);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    if (terminalEventSent.compareAndSet(false, true)) {
      recordUpstreamFailure(throwable);
      if (!clientConnected.get()) {
        return;
      }
      try {
        /*
         * 上游以异常形式结束时，也转换成统一的 agent_error SSE 事件。
         * terminalEventSent 确保不会重复发送 AgentError/AgentRunEnd 两类终态事件。
         */
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentError("unknown", toAgentException(throwable))));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        recordClientDisconnected(sendFailure);
        emitter.completeWithError(sendFailure);
      }
    }
  }

  @Override
  public void onComplete() {
    if (terminalEventSent.compareAndSet(false, true)) {
      recordUpstreamCompleted();
      if (!clientConnected.get()) {
        return;
      }
      try {
        /*
         * 理想情况下 Agent loop 会先发布 AgentRunEnd 再完成 Publisher。
         * 如果上游只触发 onComplete 而没有业务终态事件，这里补一个兜底的 AgentRunEnd，
         * 避免前端只看到连接关闭却拿不到明确的 run 结束事件。
         */
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentRunEnd(
            "unknown",
            1,
            LlmFinishReason.UNKNOWN,
            null)));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        recordClientDisconnected(sendFailure);
        emitter.completeWithError(sendFailure);
      }
    }
  }

  public void timeout() {
    if (timeoutRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.timeout(streamType);
      opsLogger.warn(
          log,
          OpsLogEventType.SSE_CONNECTION_TIMEOUT,
          logFields(SseFailureType.TIMEOUT),
          null);
    }
    recordSseFailed(SseFailureType.TIMEOUT, null);
    if (cancelUpstreamOnClientDisconnect) {
      recordPracticeStatus(OpsStatus.FAILED);
    }
    cancel();
  }

  public void clientDisconnected(Throwable throwable) {
    clientConnected.set(false);
    if (!sseTerminalRecorded.get()) {
      recordClientDisconnected(throwable);
    }
    cancel();
  }

  public void cancel() {
    if (!cancelUpstreamOnClientDisconnect) {
      clientConnected.set(false);
      return;
    }
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private void requestNextOrFinish(AgentStreamEvent event) {
    if (isTerminalEvent(event)) {
      recordTerminalEvent(event);
      terminalEventSent.set(true);
      return;
    }
    if (subscription != null) {
      subscription.request(1);
    }
  }

  private boolean isTerminalEvent(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd || event instanceof AgentStreamEvent.AgentError;
  }

  private void recordTerminalEvent(AgentStreamEvent event) {
    if (event instanceof AgentStreamEvent.AgentRunEnd) {
      recordUpstreamCompleted();
      return;
    }
    if (event instanceof AgentStreamEvent.AgentError agentError) {
      recordUpstreamFailure(agentError.error());
    }
  }

  private void recordUpstreamCompleted() {
    recordSseCompleted();
    recordPracticeStatus(OpsStatus.COMPLETED);
  }

  private void recordUpstreamFailure(Throwable throwable) {
    recordSseFailed(SseFailureType.UPSTREAM_ERROR, throwable);
    recordPracticeStatus(OpsStatus.FAILED);
  }

  private void recordSseCompleted() {
    if (sseTerminalRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.completed(streamType);
      opsLogger.info(log, OpsLogEventType.SSE_CONNECTION_COMPLETED, logFields(null));
    }
  }

  private void recordSseFailed(SseFailureType failureType, Throwable throwable) {
    if (sseTerminalRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.failed(streamType, failureType);
      opsLogger.warn(
          log,
          OpsLogEventType.SSE_CONNECTION_FAILED,
          logFields(failureType, throwable),
          null);
    }
  }

  private void recordClientDisconnected(Throwable throwable) {
    if (clientDisconnectedRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.clientDisconnected(streamType);
      opsLogger.warn(
          log,
          OpsLogEventType.SSE_CONNECTION_FAILED,
          logFields(SseFailureType.SEND_FAILURE, throwable),
          null);
    }
    recordSseFailed(SseFailureType.SEND_FAILURE, throwable);
    if (cancelUpstreamOnClientDisconnect) {
      recordPracticeStatus(OpsStatus.FAILED);
    }
  }

  private void recordPracticeStatus(OpsStatus status) {
    if (streamType == SseStreamType.PRACTICE_MESSAGE
        && practiceBusinessRecorded.compareAndSet(false, true)) {
      learningOpsRecorder.practiceMessageStream(status);
    }
  }

  private Map<String, ?> logFields(SseFailureType failureType) {
    if (failureType == null) {
      return Map.of(OpsLogFields.SSE_STREAM_TYPE, streamType.tagValue());
    }
    return Map.of(
        OpsLogFields.SSE_STREAM_TYPE, streamType.tagValue(),
        OpsLogFields.FAILURE_TYPE, failureType.tagValue());
  }

  private Map<String, ?> logFields(SseFailureType failureType, Throwable throwable) {
    if (throwable == null) {
      return logFields(failureType);
    }
    return Map.of(
        OpsLogFields.SSE_STREAM_TYPE, streamType.tagValue(),
        OpsLogFields.FAILURE_TYPE, failureType.tagValue(),
        OpsLogFields.EXCEPTION_TYPE, throwable.getClass().getSimpleName());
  }

  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException agentException) {
      return agentException;
    }
    String message = throwable.getMessage() == null ? "Agent stream failed" : throwable.getMessage();
    return new AgentException(AgentErrorCode.UNKNOWN, message, false, null, throwable);
  }
}
