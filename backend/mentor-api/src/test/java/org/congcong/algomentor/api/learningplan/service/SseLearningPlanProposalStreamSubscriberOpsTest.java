package org.congcong.algomentor.api.learningplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.SseFailureType;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseLearningPlanProposalStreamSubscriberOpsTest {

  @Test
  void terminalReadySendSuccessMarksCompletedAfterSend() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseEmitter emitter = new RecordingSseEmitter();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanProposalStreamSubscriber subscriber = subscriber(
        emitter,
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(readyEvent());
    subscriber.onComplete();

    assertThat(emitter.sentCount).isEqualTo(1);
    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_proposal",
        "completed:learning_plan_proposal");
    assertThat(learningRecorder.events).isEmpty();
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService).markCompleted(admission, AiUsage.zero(), null, null);
    verify(lifecycleService, never())
        .markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  @Test
  void terminalReadySendFailureMarksFailedWithoutCompletedMetrics() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanProposalStreamSubscriber subscriber = subscriber(
        new FailingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(readyEvent());

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_proposal",
        "clientDisconnected:learning_plan_proposal",
        "failed:learning_plan_proposal:send_failure");
    assertThat(learningRecorder.events).isEmpty();
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService, never()).markCompleted(admission, AiUsage.zero(), null, null);
    verify(lifecycleService)
        .markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  private SseLearningPlanProposalStreamSubscriber subscriber(
      SseEmitter emitter,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission,
      SseOpsRecorder sseRecorder,
      LearningOpsRecorder learningRecorder) {
    // learningRecorder is intentionally unused: proposal streams only record SSE-level metrics.
    return new SseLearningPlanProposalStreamSubscriber(
        emitter,
        new LearningPlanProposalStreamSseMapper(),
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_PROPOSAL,
        sseRecorder,
        new StructuredOpsLogger());
  }

  private LearningPlanProposalStreamEvent readyEvent() {
    return new LearningPlanProposalStreamEvent.Proposal(
        LearningPlanProposalStreamEvent.ProposalProfile.PLAN_EXTENSION,
        new LearningPlanProposalEvent.PlanExtensionReady(new LearningPlanExtensionResult(
            800L,
            801L,
            900L,
            1,
            LearningPlanProposalRevisionStatus.READY,
            List.of(),
            "补充图论训练",
            null)));
  }

  private AiRunAdmission admission() {
    AiPurposePolicy policy = new AiPurposePolicy(
        true, 50, 1, 16384, 2048, 8, true, true, false, false,
        null, null, "learning-plan-p0");
    return new AiRunAdmission(
        1L,
        "run-1",
        42L,
        AiPurpose.LEARNING_PLAN,
        AiRunSource.LEARNING_PLAN_EXTENSION_PROPOSAL,
        AiRunStatus.ADMITTED,
        "ALL",
        new AgentRunLockToken("user:42:ai:all", "node-1", "token-1", null),
        policy,
        Map.of(),
        Instant.now());
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    private int sentCount;

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
      sentCount++;
    }
  }

  private static final class FailingSseEmitter extends SseEmitter {

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
      throw new IOException("client disconnected");
    }
  }

  private static final class RecordingSubscription implements Flow.Subscription {

    private boolean cancelled;

    @Override
    public void request(long n) {
    }

    @Override
    public void cancel() {
      cancelled = true;
    }
  }

  private static final class RecordingSseOpsRecorder implements SseOpsRecorder {

    private final List<String> events = new ArrayList<>();

    @Override
    public void opened(SseStreamType streamType) {
      events.add("opened:" + streamType.tagValue());
    }

    @Override
    public void completed(SseStreamType streamType) {
      events.add("completed:" + streamType.tagValue());
    }

    @Override
    public void failed(SseStreamType streamType, SseFailureType failureType) {
      events.add("failed:" + streamType.tagValue() + ":" + failureType.tagValue());
    }

    @Override
    public void timeout(SseStreamType streamType) {
      events.add("timeout:" + streamType.tagValue());
    }

    @Override
    public void clientDisconnected(SseStreamType streamType) {
      events.add("clientDisconnected:" + streamType.tagValue());
    }
  }

  private static final class RecordingLearningOpsRecorder implements LearningOpsRecorder {

    private final List<String> events = new ArrayList<>();

    @Override
    public void learningPlanDraft(OpsStatus status) {
      events.add("draft:" + status.tagValue());
    }

    @Override
    public void practiceMessageStream(OpsStatus status) {
      events.add("practice:" + status.tagValue());
    }

    @Override
    public void practiceCodeReview(OpsStatus status) {
      events.add("review:" + status.tagValue());
    }
  }
}
