package org.congcong.algomentor.queue.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.junit.jupiter.api.Test;

class QueueTopicWorkerTest {

  @Test
  void retriesAfterDispatcherFailureWithoutExiting() throws Exception {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getConsumer().setPollInterval(Duration.ofMillis(5));
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch secondCall = new CountDownLatch(1);
    QueueDispatcher dispatcher = dispatcher((topic, mayDequeue) -> {
      if (calls.incrementAndGet() == 1) throw new IllegalStateException("transient");
      secondCall.countDown();
      return List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    });
    QueueTopicWorker worker = new QueueTopicWorker("topic", dispatcher, properties.getConsumer(), QueueMetrics.NOOP);
    Thread thread = new Thread(worker, "queue-topic-worker-test");

    thread.start();
    assertThat(secondCall.await(1, TimeUnit.SECONDS)).isTrue();
    worker.stop();
    thread.join(1_000);

    assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    assertThat(thread.isAlive()).isFalse();
  }

  @Test
  void immediatelyStartsAnotherRoundAfterDispatch() throws Exception {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getConsumer().setPollInterval(Duration.ofSeconds(5));
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch secondRound = new CountDownLatch(1);
    QueueDispatcher dispatcher = dispatcher((topic, mayDequeue) -> {
      int call = calls.incrementAndGet();
      if (call == 2) secondRound.countDown();
      return call == 1 ? List.of(QueueDispatchOutcome.DISPATCHED) : List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    });
    QueueTopicWorker worker = new QueueTopicWorker("topic", dispatcher, properties.getConsumer(), QueueMetrics.NOOP);
    Thread thread = new Thread(worker, "queue-topic-worker-test");

    thread.start();
    assertThat(secondRound.await(250, TimeUnit.MILLISECONDS)).isTrue();
    worker.stop();
    thread.join(1_000);
  }

  private QueueDispatcher dispatcher(DispatchBehavior behavior) {
    return new QueueDispatcher(null, null, null) {
      @Override
      public List<QueueDispatchOutcome> dispatchRound(String topic, java.util.function.BooleanSupplier mayDequeue) {
        return behavior.dispatch(topic, mayDequeue);
      }
    };
  }

  @FunctionalInterface
  private interface DispatchBehavior {
    List<QueueDispatchOutcome> dispatch(String topic, java.util.function.BooleanSupplier mayDequeue);
  }
}
