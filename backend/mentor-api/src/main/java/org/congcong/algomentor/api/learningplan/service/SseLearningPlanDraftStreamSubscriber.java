package org.congcong.algomentor.api.learningplan.service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
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

public class SseLearningPlanDraftStreamSubscriber implements Flow.Subscriber<LearningPlanDraftStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(SseLearningPlanDraftStreamSubscriber.class);

  private final SseEmitter emitter;
  private final LearningPlanDraftStreamSseMapper mapper;
  private final AiRunLifecycleService lifecycleService;
  private final AiRunAdmission admission;
  private final SseStreamType streamType;
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;
  private final AtomicBoolean terminal = new AtomicBoolean(false);
  private final AtomicBoolean sseTerminalRecorded = new AtomicBoolean(false);
  private final AtomicBoolean businessRecorded = new AtomicBoolean(false);
  private final AtomicBoolean clientDisconnectedRecorded = new AtomicBoolean(false);
  private final AtomicBoolean timeoutRecorded = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  public SseLearningPlanDraftStreamSubscriber(
      SseEmitter emitter,
      LearningPlanDraftStreamSseMapper mapper,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission
  ) {
    this(
        emitter,
        mapper,
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_DRAFT,
        NoopOpsRecorders.sse(),
        NoopOpsRecorders.learning(),
        new StructuredOpsLogger());
  }

  public SseLearningPlanDraftStreamSubscriber(
      SseEmitter emitter,
      LearningPlanDraftStreamSseMapper mapper,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission,
      SseStreamType streamType,
      SseOpsRecorder sseOpsRecorder,
      LearningOpsRecorder learningOpsRecorder,
      StructuredOpsLogger opsLogger
  ) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
    this.admission = Objects.requireNonNull(admission, "admission must not be null");
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
    subscription.request(1);
  }

  @Override
  public void onNext(LearningPlanDraftStreamEvent event) {
    LearningPlanDraftEvent terminalDraftEvent = terminalDraftEvent(event);
    if (terminalDraftEvent != null) {
      markLifecycle(terminalDraftEvent);
      recordTerminalDraftEvent(terminalDraftEvent);
      terminal.set(true);
    }
    try {
      emitter.send(mapper.toSseEvent(event));
      if (terminalDraftEvent != null) {
        emitter.complete();
        cancel();
        return;
      }
      subscription.request(1);
    } catch (IOException | RuntimeException exception) {
      if (terminalDraftEvent == null) {
        recordFailed(SseFailureType.SEND_FAILURE, exception);
      } else {
        recordClientDisconnected(exception);
      }
      cancel();
      emitter.completeWithError(exception);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      recordFailed(SseFailureType.UPSTREAM_ERROR, throwable);
      emitter.completeWithError(throwable);
    }
  }

  @Override
  public void onComplete() {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      recordCompleted();
      emitter.complete();
    }
  }

  public void timeout() {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      recordBusinessStatus(OpsStatus.FAILED);
    }
    if (timeoutRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.timeout(streamType);
      opsLogger.warn(
          log,
          OpsLogEventType.SSE_CONNECTION_TIMEOUT,
          logFields(SseFailureType.TIMEOUT),
          null);
    }
    recordSseFailed(SseFailureType.TIMEOUT, null);
    cancel();
  }

  public void clientDisconnected(Throwable throwable) {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      recordBusinessStatus(OpsStatus.FAILED);
    }
    if (!sseTerminalRecorded.get()) {
      recordClientDisconnected(throwable);
      recordSseFailed(SseFailureType.SEND_FAILURE, throwable);
    }
    cancel();
  }

  public void cancel() {
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private boolean isTerminalDraftEvent(LearningPlanDraftEvent event) {
    return event instanceof LearningPlanDraftEvent.DraftReady || event instanceof LearningPlanDraftEvent.DraftError;
  }

  private LearningPlanDraftEvent terminalDraftEvent(LearningPlanDraftStreamEvent event) {
    if (event instanceof LearningPlanDraftStreamEvent.Draft draft && isTerminalDraftEvent(draft.event())) {
      return draft.event();
    }
    return null;
  }

  private void markLifecycle(LearningPlanDraftEvent event) {
    if (event instanceof LearningPlanDraftEvent.DraftReady) {
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      return;
    }
    lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  private void recordTerminalDraftEvent(LearningPlanDraftEvent event) {
    if (event instanceof LearningPlanDraftEvent.DraftReady) {
      recordCompleted();
      return;
    }
    recordFailed(SseFailureType.UPSTREAM_ERROR, null);
  }

  private void recordCompleted() {
    recordSseCompleted();
    recordBusinessStatus(OpsStatus.COMPLETED);
  }

  private void recordSseCompleted() {
    if (sseTerminalRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.completed(streamType);
      opsLogger.info(log, OpsLogEventType.SSE_CONNECTION_COMPLETED, logFields(null));
    }
  }

  private void recordFailed(SseFailureType failureType, Throwable throwable) {
    recordSseFailed(failureType, throwable);
    recordBusinessStatus(OpsStatus.FAILED);
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
  }

  private void recordBusinessStatus(OpsStatus status) {
    if (businessRecorded.compareAndSet(false, true)) {
      learningOpsRecorder.learningPlanDraft(status);
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
}
