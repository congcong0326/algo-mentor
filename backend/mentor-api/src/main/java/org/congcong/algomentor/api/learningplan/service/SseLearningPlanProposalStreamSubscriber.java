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
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
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

public class SseLearningPlanProposalStreamSubscriber implements Flow.Subscriber<LearningPlanProposalStreamEvent> {

  private static final Logger log = LoggerFactory.getLogger(SseLearningPlanProposalStreamSubscriber.class);

  private final SseEmitter emitter;
  private final LearningPlanProposalStreamSseMapper mapper;
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

  public SseLearningPlanProposalStreamSubscriber(
      SseEmitter emitter,
      LearningPlanProposalStreamSseMapper mapper,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission
  ) {
    this(
        emitter,
        mapper,
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_PROPOSAL,
        NoopOpsRecorders.sse(),
        NoopOpsRecorders.learning(),
        new StructuredOpsLogger());
  }

  public SseLearningPlanProposalStreamSubscriber(
      SseEmitter emitter,
      LearningPlanProposalStreamSseMapper mapper,
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
  public void onNext(LearningPlanProposalStreamEvent event) {
    LearningPlanProposalEvent terminalProposalEvent = terminalProposalEvent(event);
    if (terminalProposalEvent != null) {
      markLifecycle(terminalProposalEvent);
      recordTerminalProposalEvent(terminalProposalEvent);
      terminal.set(true);
    }
    try {
      emitter.send(mapper.toSseEvent(event));
      if (terminalProposalEvent != null) {
        emitter.complete();
        cancel();
        return;
      }
      subscription.request(1);
    } catch (IOException | RuntimeException exception) {
      if (terminalProposalEvent == null) {
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
      recordBusinessStatus(OpsStatus.FAILED, SseFailureType.TIMEOUT, null);
    }
    if (timeoutRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.timeout(streamType);
      opsLogger.warn(log, OpsLogEventType.SSE_CONNECTION_TIMEOUT, logFields(SseFailureType.TIMEOUT), null);
    }
    recordSseFailed(SseFailureType.TIMEOUT, null);
    cancel();
  }

  public void clientDisconnected(Throwable throwable) {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      recordBusinessStatus(OpsStatus.FAILED, SseFailureType.SEND_FAILURE, throwable);
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

  private boolean isTerminalProposalEvent(LearningPlanProposalEvent event) {
    return event instanceof LearningPlanProposalEvent.DraftRevisionReady
        || event instanceof LearningPlanProposalEvent.PlanExtensionReady
        || event instanceof LearningPlanProposalEvent.ProposalError;
  }

  private LearningPlanProposalEvent terminalProposalEvent(LearningPlanProposalStreamEvent event) {
    if (event instanceof LearningPlanProposalStreamEvent.Proposal proposal && isTerminalProposalEvent(proposal.event())) {
      return proposal.event();
    }
    return null;
  }

  private void markLifecycle(LearningPlanProposalEvent event) {
    if (event instanceof LearningPlanProposalEvent.DraftRevisionReady
        || event instanceof LearningPlanProposalEvent.PlanExtensionReady) {
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      return;
    }
    lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  private void recordTerminalProposalEvent(LearningPlanProposalEvent event) {
    if (event instanceof LearningPlanProposalEvent.DraftRevisionReady
        || event instanceof LearningPlanProposalEvent.PlanExtensionReady) {
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
    recordBusinessStatus(OpsStatus.FAILED, failureType, throwable);
  }

  private void recordSseFailed(SseFailureType failureType, Throwable throwable) {
    if (sseTerminalRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.failed(streamType, failureType);
      opsLogger.warn(log, OpsLogEventType.SSE_CONNECTION_FAILED, logFields(failureType, throwable), null);
    }
  }

  private void recordClientDisconnected(Throwable throwable) {
    if (clientDisconnectedRecorded.compareAndSet(false, true)) {
      sseOpsRecorder.clientDisconnected(streamType);
      opsLogger.warn(log, OpsLogEventType.SSE_CONNECTION_FAILED, logFields(SseFailureType.SEND_FAILURE, throwable), null);
    }
  }

  private void recordBusinessStatus(OpsStatus status) {
    recordBusinessStatus(status, null, null);
  }

  private void recordBusinessStatus(OpsStatus status, SseFailureType failureType, Throwable throwable) {
    if (businessRecorded.compareAndSet(false, true)) {
      learningOpsRecorder.learningPlanDraft(status);
      if (status == OpsStatus.FAILED) {
        opsLogger.warn(
            log,
            OpsLogEventType.LEARNING_PLAN_DRAFT_FAILED,
            logFields(failureType == null ? SseFailureType.UPSTREAM_ERROR : failureType, throwable),
            null);
      }
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
