package org.congcong.algomentor.api.learningplan.service;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseLearningPlanDraftStreamSubscriber implements Flow.Subscriber<LearningPlanDraftStreamEvent> {

  private final SseEmitter emitter;
  private final LearningPlanDraftStreamSseMapper mapper;
  private final AiRunLifecycleService lifecycleService;
  private final AiRunAdmission admission;
  private final AtomicBoolean terminal = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  public SseLearningPlanDraftStreamSubscriber(
      SseEmitter emitter,
      LearningPlanDraftStreamSseMapper mapper,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission
  ) {
    this.emitter = emitter;
    this.mapper = mapper;
    this.lifecycleService = lifecycleService;
    this.admission = admission;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(LearningPlanDraftStreamEvent event) {
    try {
      emitter.send(mapper.toSseEvent(event));
      if (event instanceof LearningPlanDraftStreamEvent.Draft draft && isTerminalDraftEvent(draft.event())) {
        markLifecycle(draft.event());
        terminal.set(true);
        emitter.complete();
        cancel();
        return;
      }
      subscription.request(1);
    } catch (IOException | RuntimeException exception) {
      cancel();
      emitter.completeWithError(exception);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      emitter.completeWithError(throwable);
    }
  }

  @Override
  public void onComplete() {
    if (terminal.compareAndSet(false, true)) {
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      emitter.complete();
    }
  }

  public void cancel() {
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private boolean isTerminalDraftEvent(LearningPlanDraftEvent event) {
    return event instanceof LearningPlanDraftEvent.DraftReady || event instanceof LearningPlanDraftEvent.DraftError;
  }

  private void markLifecycle(LearningPlanDraftEvent event) {
    if (event instanceof LearningPlanDraftEvent.DraftReady) {
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      return;
    }
    lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }
}
