package org.congcong.algomentor.api.learningplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.SseFailureType;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseLearningPlanDraftStreamSubscriberOpsTest {

  @Test
  void recordsDraftReadyAsCompleted() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanDraftStreamSubscriber subscriber = subscriber(
        new RecordingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new LearningPlanDraftStreamEvent.Draft(
        new LearningPlanDraftEvent.DraftReady(draftResult())));
    subscriber.onComplete();

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_draft",
        "completed:learning_plan_draft");
    assertThat(learningRecorder.events).containsExactly("draft:completed");
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService).markCompleted(admission, AiUsage.zero(), null, null);
  }

  @Test
  void terminalDraftReadySendFailureKeepsBusinessCompleted() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanDraftStreamSubscriber subscriber = subscriber(
        new FailingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new LearningPlanDraftStreamEvent.Draft(
        new LearningPlanDraftEvent.DraftReady(draftResult())));

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_draft",
        "completed:learning_plan_draft",
        "clientDisconnected:learning_plan_draft");
    assertThat(learningRecorder.events).containsExactly("draft:completed");
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService).markCompleted(admission, AiUsage.zero(), null, null);
  }

  @Test
  void recordsDraftErrorAsFailed() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanDraftStreamSubscriber subscriber = subscriber(
        new RecordingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new LearningPlanDraftStreamEvent.Draft(
        new LearningPlanDraftEvent.DraftError("AI_UNKNOWN", "failed", true)));
    subscriber.onError(new RuntimeException("late failure"));

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_draft",
        "failed:learning_plan_draft:upstream_error");
    assertThat(learningRecorder.events).containsExactly("draft:failed");
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService)
        .markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  @Test
  void clientDisconnectedCallbackRecordsFailedAndCancelsUpstream() throws IOException {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanDraftStreamSubscriber subscriber = subscriber(
        new RecordingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.clientDisconnected(new IOException("client disconnected"));
    subscriber.onComplete();

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_draft",
        "clientDisconnected:learning_plan_draft",
        "failed:learning_plan_draft:send_failure");
    assertThat(learningRecorder.events).containsExactly("draft:failed");
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService)
        .markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  @Test
  void recordsTimeout() {
    AiRunLifecycleService lifecycleService = mock(AiRunLifecycleService.class);
    AiRunAdmission admission = admission();
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLearningPlanDraftStreamSubscriber subscriber = subscriber(
        new RecordingSseEmitter(),
        lifecycleService,
        admission,
        sseRecorder,
        learningRecorder);

    subscriber.onSubscribe(subscription);
    subscriber.timeout();
    subscriber.onError(new RuntimeException("late failure"));

    assertThat(sseRecorder.events).containsExactly(
        "opened:learning_plan_draft",
        "timeout:learning_plan_draft",
        "failed:learning_plan_draft:timeout");
    assertThat(learningRecorder.events).containsExactly("draft:failed");
    assertThat(subscription.cancelled).isTrue();
    verify(lifecycleService)
        .markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
  }

  private SseLearningPlanDraftStreamSubscriber subscriber(
      SseEmitter emitter,
      AiRunLifecycleService lifecycleService,
      AiRunAdmission admission,
      SseOpsRecorder sseRecorder,
      LearningOpsRecorder learningRecorder) {
    return new SseLearningPlanDraftStreamSubscriber(
        emitter,
        new LearningPlanDraftStreamSseMapper(),
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_DRAFT,
        sseRecorder,
        learningRecorder,
        new StructuredOpsLogger());
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
        AiRunSource.LEARNING_PLAN_DRAFT,
        AiRunStatus.ADMITTED,
        "ALL",
        new AgentRunLockToken("user:42:ai:all", "node-1", "token-1", null),
        policy,
        Map.of(),
        Instant.now());
  }

  private LearningPlanDraftResult draftResult() {
    return new LearningPlanDraftResult(
        100L,
        LearningPlanDraftStatus.GENERATED,
        "ready",
        List.of(),
        null);
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
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
