package org.congcong.algomentor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsLogEventType;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.SseFailureType;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseLlmStreamSubscriberOpsTest {

  @Test
  void recordsCompletedPracticeStream() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        true);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, null));
    subscriber.onComplete();

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "completed:practice_message");
    assertThat(learningRecorder.events).containsExactly("practice:completed");
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void recordsSendFailureAsFailedAndClientDisconnected() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new FailingSseEmitter(),
        sseRecorder,
        learningRecorder,
        true);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("partial")));
    subscriber.onError(new AgentException(AgentErrorCode.LLM_STREAM_FAILED, "upstream failed"));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "clientDisconnected:practice_message",
        "failed:practice_message:send_failure");
    assertThat(learningRecorder.events).containsExactly("practice:failed");
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void clientDisconnectedCallbackRecordsFailedAndCancelsUpstream() throws IOException {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        true);

    subscriber.onSubscribe(subscription);
    subscriber.clientDisconnected(new IOException("client disconnected"));
    subscriber.onComplete();

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "clientDisconnected:practice_message",
        "failed:practice_message:send_failure");
    assertThat(learningRecorder.events).containsExactly("practice:failed");
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void drainModeSendFailureDoesNotFailPracticeBusinessBeforeLaterRunEnd() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new FailingSseEmitter(),
        sseRecorder,
        learningRecorder,
        false);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("partial")));
    subscriber.onNext(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, null));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "clientDisconnected:practice_message",
        "failed:practice_message:send_failure");
    assertThat(learningRecorder.events).containsExactly("practice:completed");
    assertThat(subscription.cancelled).isFalse();
    assertThat(subscription.requested).isEqualTo(2);
  }

  @Test
  void drainModeClientDisconnectedCallbackKeepsBusinessOpenUntilRunEnd() throws IOException {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        false);

    subscriber.onSubscribe(subscription);
    subscriber.clientDisconnected(new IOException("client disconnected"));
    subscriber.onNext(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, null));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "clientDisconnected:practice_message",
        "failed:practice_message:send_failure");
    assertThat(learningRecorder.events).containsExactly("practice:completed");
    assertThat(subscription.cancelled).isFalse();
  }

  @Test
  void drainModeTerminalAgentErrorAfterDisconnectedRecordsPracticeFailed() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new FailingSseEmitter(),
        sseRecorder,
        learningRecorder,
        false);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("partial")));
    subscriber.onNext(new AgentStreamEvent.AgentError(
        "run-1",
        new AgentException(AgentErrorCode.LLM_STREAM_FAILED, "upstream failed")));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "clientDisconnected:practice_message",
        "failed:practice_message:send_failure");
    assertThat(learningRecorder.events).containsExactly("practice:failed");
    assertThat(subscription.cancelled).isFalse();
  }

  @Test
  void logsPracticeMessageStreamFailureWhenBusinessFails() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingStructuredOpsLogger opsLogger = new RecordingStructuredOpsLogger();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        opsLogger,
        true);

    subscriber.onSubscribe(new RecordingSubscription());
    subscriber.onNext(new AgentStreamEvent.AgentError(
        "run-1",
        new AgentException(AgentErrorCode.LLM_STREAM_FAILED, "upstream failed")));

    assertThat(opsLogger.warnEvents)
        .contains("eventType=practice_message_stream_failed exceptionType=AgentException "
            + "sseStreamType=practice_message failureType=upstream_error");
  }

  @Test
  void recordsTimeout() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        true);

    subscriber.onSubscribe(subscription);
    subscriber.timeout();
    subscriber.onError(new AgentException(AgentErrorCode.LLM_STREAM_FAILED, "late failure"));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "timeout:practice_message",
        "failed:practice_message:timeout");
    assertThat(learningRecorder.events).containsExactly("practice:failed");
    assertThat(subscription.cancelled).isTrue();
  }

  @Test
  void drainModeTimeoutDoesNotFailPracticeBusinessBeforeLaterRunEnd() {
    RecordingSseOpsRecorder sseRecorder = new RecordingSseOpsRecorder();
    RecordingLearningOpsRecorder learningRecorder = new RecordingLearningOpsRecorder();
    RecordingSubscription subscription = new RecordingSubscription();
    SseLlmStreamSubscriber subscriber = practiceSubscriber(
        new RecordingSseEmitter(),
        sseRecorder,
        learningRecorder,
        false);

    subscriber.onSubscribe(subscription);
    subscriber.timeout();
    subscriber.onNext(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, null));

    assertThat(sseRecorder.events).containsExactly(
        "opened:practice_message",
        "timeout:practice_message",
        "failed:practice_message:timeout");
    assertThat(learningRecorder.events).containsExactly("practice:completed");
    assertThat(subscription.cancelled).isFalse();
  }

  private SseLlmStreamSubscriber practiceSubscriber(
      SseEmitter emitter,
      SseOpsRecorder sseRecorder,
      LearningOpsRecorder learningRecorder,
      boolean cancelUpstreamOnClientDisconnect) {
    return new SseLlmStreamSubscriber(
        emitter,
        new LlmStreamSseMapper(),
        cancelUpstreamOnClientDisconnect,
        SseStreamType.PRACTICE_MESSAGE,
        sseRecorder,
        learningRecorder,
        new StructuredOpsLogger());
  }

  private SseLlmStreamSubscriber practiceSubscriber(
      SseEmitter emitter,
      SseOpsRecorder sseRecorder,
      LearningOpsRecorder learningRecorder,
      StructuredOpsLogger opsLogger,
      boolean cancelUpstreamOnClientDisconnect) {
    return new SseLlmStreamSubscriber(
        emitter,
        new LlmStreamSseMapper(),
        cancelUpstreamOnClientDisconnect,
        SseStreamType.PRACTICE_MESSAGE,
        sseRecorder,
        learningRecorder,
        opsLogger);
  }

  private static final class RecordingSseEmitter extends SseEmitter {

    @Override
    public synchronized void send(SseEventBuilder builder) {
    }
  }

  private static final class FailingSseEmitter extends SseEmitter {

    @Override
    public synchronized void send(SseEventBuilder builder) throws IOException {
      throw new IOException("client disconnected");
    }
  }

  private static final class RecordingSubscription implements Flow.Subscription {

    private long requested;
    private boolean cancelled;

    @Override
    public void request(long n) {
      requested += n;
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

  private static final class RecordingStructuredOpsLogger extends StructuredOpsLogger {

    private final List<String> warnEvents = new ArrayList<>();

    @Override
    public void warn(
        org.slf4j.Logger log,
        OpsLogEventType eventType,
        java.util.Map<String, ?> fields,
        Throwable throwable) {
      warnEvents.add(format(eventType, fields));
    }
  }
}
