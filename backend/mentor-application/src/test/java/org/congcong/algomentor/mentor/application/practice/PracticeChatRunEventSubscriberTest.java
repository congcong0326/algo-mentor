package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;

class PracticeChatRunEventSubscriberTest {

  @Test
  void continuesRequestingAfterEventStoreFailureAndTouchesOnRunEnd() {
    RecordingSubscription subscription = new RecordingSubscription();
    List<String> appendedRunUuids = new ArrayList<>();
    int[] ended = {0};
    PracticeChatRunEventSubscriber subscriber = new PracticeChatRunEventSubscriber(
        "run-1",
        (runUuid, event) -> {
          appendedRunUuids.add(runUuid);
          throw new IllegalStateException("Redis unavailable");
        },
        () -> ended[0]++);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, java.util.Map.of()));

    assertThat(appendedRunUuids).containsExactly("run-1");
    assertThat(ended[0]).isEqualTo(1);
    assertThat(subscription.requests).containsExactly(1L, 1L);
  }

  @Test
  void doesNotTouchSessionForNonTerminalEvents() {
    RecordingSubscription subscription = new RecordingSubscription();
    int[] ended = {0};
    PracticeChatRunEventSubscriber subscriber = new PracticeChatRunEventSubscriber(
        "run-1", (runUuid, event) -> {}, () -> ended[0]++);

    subscriber.onSubscribe(subscription);
    subscriber.onNext(new AgentStreamEvent.AgentStepStart("run-1", 1));

    assertThat(ended[0]).isZero();
    assertThat(subscription.requests).containsExactly(1L, 1L);
  }

  @Test
  void writesAnAgentErrorWhenThePublisherTerminatesWithAnError() {
    RecordingSubscription subscription = new RecordingSubscription();
    List<AgentStreamEvent> events = new ArrayList<>();
    PracticeChatRunEventSubscriber subscriber = new PracticeChatRunEventSubscriber(
        "run-1", (runUuid, event) -> events.add(event));

    subscriber.onSubscribe(subscription);
    subscriber.onError(new AgentException(
        AgentErrorCode.LLM_STREAM_FAILED,
        "Our servers are currently overloaded. Please try again later.",
        true,
        java.util.Map.of(),
        null));
    subscriber.onComplete();

    assertThat(events).singleElement().isInstanceOfSatisfying(AgentStreamEvent.AgentError.class, error -> {
      assertThat(error.runId()).isEqualTo("run-1");
      assertThat(error.error().getMessage()).isEqualTo(
          "Our servers are currently overloaded. Please try again later.");
    });
  }

  @Test
  void writesAnAgentErrorWhenThePublisherCompletesWithoutABusinessTerminalEvent() {
    List<AgentStreamEvent> events = new ArrayList<>();
    PracticeChatRunEventSubscriber subscriber = new PracticeChatRunEventSubscriber(
        "run-1", (runUuid, event) -> events.add(event));

    subscriber.onComplete();

    assertThat(events).singleElement().isInstanceOfSatisfying(AgentStreamEvent.AgentError.class, error -> {
      assertThat(error.error().code()).isEqualTo(AgentErrorCode.UNKNOWN);
      assertThat(error.error().getMessage()).isEqualTo("Agent stream completed without a terminal event");
    });
  }

  private static final class RecordingSubscription implements Flow.Subscription {
    private final List<Long> requests = new ArrayList<>();

    @Override
    public void request(long n) {
      requests.add(n);
    }

    @Override
    public void cancel() {
    }
  }
}
