package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;

class SingleSubscriberAgentStreamPublisherTest {

  private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger(1);
  private static final AgentExecutor TEST_EXECUTOR = new AgentExecutor() {
    @Override
    public void execute(Runnable task) {
      Thread worker = new Thread(task, "agent-publisher-test-" + WORKER_SEQUENCE.getAndIncrement());
      worker.setDaemon(true);
      worker.start();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }
  };

  @Test
  void waitsForDemandAndPreservesEventOrder() {
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch secondEmitStarted = new CountDownLatch(1);
    SingleSubscriberAgentStreamPublisher publisher = new SingleSubscriberAgentStreamPublisher(
        new AgentCancellationToken(),
        TEST_EXECUTOR,
        sink -> {
          workerStarted.countDown();
          sink.emit(new AgentStreamEvent.AgentStepStart("run-1", 1));
          secondEmitStarted.countDown();
          sink.emit(new AgentStreamEvent.AgentStepStart("run-1", 2));
        },
        ignored -> {
        });
    ManualDemandSubscriber subscriber = new ManualDemandSubscriber();

    publisher.subscribe(subscriber);

    await(workerStarted);
    assertThat(subscriber.poll(100)).isNull();

    subscriber.request(1);
    assertThat(subscriber.poll(1_000))
        .isEqualTo(new AgentStreamEvent.AgentStepStart("run-1", 1));
    await(secondEmitStarted);
    assertThat(subscriber.poll(100)).isNull();

    subscriber.request(1);
    assertThat(subscriber.poll(1_000))
        .isEqualTo(new AgentStreamEvent.AgentStepStart("run-1", 2));
    subscriber.awaitCompletion();
    assertThat(subscriber.error.get()).isNull();
  }

  @Test
  void rejectsDuplicateSubscriberWithStableError() {
    SingleSubscriberAgentStreamPublisher publisher = new SingleSubscriberAgentStreamPublisher(
        new AgentCancellationToken(),
        TEST_EXECUTOR,
        ignored -> {
        },
        ignored -> {
        });
    CancellingOnSubscribeSubscriber first = new CancellingOnSubscribeSubscriber();
    TerminalRecordingSubscriber duplicate = new TerminalRecordingSubscriber();

    publisher.subscribe(first);
    publisher.subscribe(duplicate);

    duplicate.awaitTerminal();
    assertThat(duplicate.error.get())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agent stream supports only one subscriber");
    assertThat(duplicate.completed.get()).isFalse();
  }

  @Test
  void invalidDemandFailsSubscriptionAndCancelsWorkerTask() {
    AgentCancellationToken cancellationToken = new AgentCancellationToken();
    AtomicBoolean workerStarted = new AtomicBoolean(false);
    CountDownLatch workerCompleted = new CountDownLatch(1);
    SingleSubscriberAgentStreamPublisher publisher = new SingleSubscriberAgentStreamPublisher(
        cancellationToken,
        TEST_EXECUTOR,
        ignored -> {
          workerStarted.set(true);
          workerCompleted.countDown();
        },
        ignored -> {
        });
    TerminalRecordingSubscriber subscriber = new TerminalRecordingSubscriber() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(0);
      }
    };

    publisher.subscribe(subscriber);

    subscriber.awaitTerminal();
    assertThat(subscriber.error.get())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Flow request count must be positive: 0");
    await(workerCompleted);
    assertThat(cancellationToken.isCancelled()).isTrue();
    assertThat(workerStarted).isTrue();
  }

  @Test
  void suppressesEventsAfterFirstBusinessTerminalEvent() {
    AtomicReference<Boolean> firstDelivered = new AtomicReference<>();
    AtomicReference<Boolean> secondDelivered = new AtomicReference<>();
    SingleSubscriberAgentStreamPublisher publisher = new SingleSubscriberAgentStreamPublisher(
        new AgentCancellationToken(),
        TEST_EXECUTOR,
        sink -> {
          firstDelivered.set(sink.emit(new AgentStreamEvent.AgentError(
              "run-1",
              new AgentException(AgentErrorCode.UNKNOWN, "failed"))));
          secondDelivered.set(sink.emit(new AgentStreamEvent.AgentRunEnd(
              "run-1",
              1,
              LlmFinishReason.ERROR,
              null)));
        },
        ignored -> {
        });
    CollectingSubscriber subscriber = new CollectingSubscriber();

    publisher.subscribe(subscriber);

    subscriber.awaitTerminal();
    assertThat(firstDelivered).hasValue(true);
    assertThat(secondDelivered).hasValue(false);
    assertThat(subscriber.events).singleElement().isInstanceOf(AgentStreamEvent.AgentError.class);
    assertThat(subscriber.completed.get()).isTrue();
    assertThat(subscriber.error.get()).isNull();
  }

  @Test
  void completedTaskCannotInterruptNextTaskOnReusedWorker() {
    ExecutorService workerPool = Executors.newSingleThreadExecutor(runnable -> {
      Thread worker = new Thread(runnable, "agent-publisher-reused-worker");
      worker.setDaemon(true);
      return worker;
    });
    AgentExecutor pooledExecutor = new AgentExecutor() {
      @Override
      public void execute(Runnable task) {
        workerPool.execute(task);
      }

      @Override
      public boolean isShutdown() {
        return workerPool.isShutdown();
      }
    };
    AgentCancellationToken completedRunToken = new AgentCancellationToken();
    AtomicReference<Thread> firstWorker = new AtomicReference<>();
    SingleSubscriberAgentStreamPublisher publisher = new SingleSubscriberAgentStreamPublisher(
        completedRunToken,
        pooledExecutor,
        ignored -> firstWorker.set(Thread.currentThread()),
        ignored -> {
        });
    TerminalRecordingSubscriber subscriber = new TerminalRecordingSubscriber();
    CountDownLatch nextTaskStarted = new CountDownLatch(1);
    CountDownLatch releaseNextTask = new CountDownLatch(1);
    CountDownLatch nextTaskCompleted = new CountDownLatch(1);
    AtomicReference<Thread> nextWorker = new AtomicReference<>();
    AtomicBoolean nextTaskInterrupted = new AtomicBoolean(false);

    try {
      publisher.subscribe(subscriber);
      subscriber.awaitTerminal();
      workerPool.execute(() -> {
        nextWorker.set(Thread.currentThread());
        nextTaskStarted.countDown();
        try {
          releaseNextTask.await();
        } catch (InterruptedException interrupted) {
          nextTaskInterrupted.set(true);
          Thread.currentThread().interrupt();
        } finally {
          nextTaskCompleted.countDown();
        }
      });
      await(nextTaskStarted);

      completedRunToken.cancel();
      releaseNextTask.countDown();
      await(nextTaskCompleted);

      assertThat(nextWorker.get()).isSameAs(firstWorker.get());
      assertThat(nextTaskInterrupted).isFalse();
    } finally {
      releaseNextTask.countDown();
      workerPool.shutdownNow();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private static class TerminalRecordingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    final CountDownLatch terminal = new CountDownLatch(1);
    final AtomicReference<Throwable> error = new AtomicReference<>();
    final AtomicBoolean completed = new AtomicBoolean(false);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
    }

    @Override
    public void onError(Throwable throwable) {
      error.set(throwable);
      terminal.countDown();
    }

    @Override
    public void onComplete() {
      completed.set(true);
      terminal.countDown();
    }

    void awaitTerminal() {
      await(terminal);
    }
  }

  private static final class CollectingSubscriber extends TerminalRecordingSubscriber {
    private final List<AgentStreamEvent> events = new ArrayList<>();

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }
  }

  private static final class ManualDemandSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final BlockingQueue<AgentStreamEvent> events = new LinkedBlockingQueue<>();
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      error.set(throwable);
      completed.countDown();
    }

    @Override
    public void onComplete() {
      completed.countDown();
    }

    private void request(long count) {
      subscription.request(count);
    }

    private AgentStreamEvent poll(long timeoutMillis) {
      try {
        return events.poll(timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }

    private void awaitCompletion() {
      await(completed);
    }
  }

  private static final class CancellingOnSubscribeSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.cancel();
    }

    @Override
    public void onNext(AgentStreamEvent item) {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }
  }
}
