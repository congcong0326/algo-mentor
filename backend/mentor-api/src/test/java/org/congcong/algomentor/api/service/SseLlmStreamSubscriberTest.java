package org.congcong.algomentor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseLlmStreamSubscriberTest {

  @Test
  void clientDisconnectCanLeaveUpstreamRunActive() {
    FailingSseEmitter emitter = new FailingSseEmitter();
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(
        emitter,
        new LlmStreamSseMapper(),
        false);
    RecordingSubscription subscription = new RecordingSubscription();

    subscriber.onSubscribe(subscription);
    subscriber.onNext(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("partial")));

    assertThat(subscription.cancelled).isFalse();
    assertThat(subscription.requested).isEqualTo(2);
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
}
