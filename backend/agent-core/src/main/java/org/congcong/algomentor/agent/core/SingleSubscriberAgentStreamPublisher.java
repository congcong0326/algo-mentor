package org.congcong.algomentor.agent.core;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import org.congcong.algomentor.common.trace.RequestTraceContext;

/**
 * 单订阅者、同步投递的 Agent 事件出口。
 *
 * <p>工作线程只有在下游声明 demand 后才会继续投递，慢消费者因此会自然阻塞自己的 Agent run，
 * 不需要额外的异步缓冲或公共线程池。</p>
 */
final class SingleSubscriberAgentStreamPublisher
    implements Flow.Publisher<AgentStreamEvent>, AgentStreamEventSink {

  static final String WORKER_THREAD_NAME = "agent-loop-stream";

  private final Object stateMonitor = new Object();
  private final Object signalMonitor = new Object();
  private final AgentCancellationToken cancellationToken;
  private final Consumer<AgentStreamEventSink> workerTask;

  private Flow.Subscriber<? super AgentStreamEvent> subscriber;
  private boolean subscribed;
  private boolean cancelled;
  private boolean terminated;
  private boolean terminalEventEmitted;
  private long demand;

  SingleSubscriberAgentStreamPublisher(
      AgentCancellationToken cancellationToken,
      Consumer<AgentStreamEventSink> workerTask
  ) {
    this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken must not be null");
    this.workerTask = Objects.requireNonNull(workerTask, "workerTask must not be null");
  }

  @Override
  public void subscribe(Flow.Subscriber<? super AgentStreamEvent> nextSubscriber) {
    Objects.requireNonNull(nextSubscriber, "subscriber must not be null");
    boolean duplicate;
    synchronized (stateMonitor) {
      duplicate = subscribed;
      if (!duplicate) {
        subscribed = true;
        subscriber = nextSubscriber;
      }
    }
    if (duplicate) {
      rejectDuplicateSubscriber(nextSubscriber);
      return;
    }

    try {
      nextSubscriber.onSubscribe(new AgentStreamSubscription());
    } catch (RuntimeException subscriberFailure) {
      cancel();
      throw subscriberFailure;
    }

    startWorker();
  }

  @Override
  public boolean emit(AgentStreamEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    Flow.Subscriber<? super AgentStreamEvent> currentSubscriber;
    synchronized (stateMonitor) {
      while (demand == 0 && isActiveLocked()) {
        try {
          stateMonitor.wait();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      if (!isActiveLocked() || terminalEventEmitted) {
        return false;
      }
      if (demand != Long.MAX_VALUE) {
        demand--;
      }
      if (isTerminalEvent(event)) {
        terminalEventEmitted = true;
      }
      currentSubscriber = subscriber;
    }

    synchronized (signalMonitor) {
      synchronized (stateMonitor) {
        if (!isActiveLocked()) {
          return false;
        }
      }
      try {
        currentSubscriber.onNext(event);
        return true;
      } catch (RuntimeException subscriberFailure) {
        cancel();
        throw subscriberFailure;
      }
    }
  }

  private void startWorker() {
    Thread worker = new Thread(
        RequestTraceContext.wrap(this::runWorker),
        WORKER_THREAD_NAME);
    worker.setDaemon(true);
    cancellationToken.worker(worker);
    worker.start();
  }

  private void runWorker() {
    try {
      workerTask.accept(this);
      complete();
    } catch (Throwable failure) {
      fail(failure);
    }
  }

  private void request(long count) {
    if (count <= 0) {
      fail(new IllegalArgumentException("Flow request count must be positive: " + count));
      return;
    }
    synchronized (stateMonitor) {
      if (!isActiveLocked()) {
        return;
      }
      demand = addCap(demand, count);
      stateMonitor.notifyAll();
    }
  }

  private void cancel() {
    synchronized (stateMonitor) {
      if (cancelled || terminated) {
        return;
      }
      cancelled = true;
      stateMonitor.notifyAll();
    }
    cancellationToken.cancel();
  }

  private void complete() {
    Flow.Subscriber<? super AgentStreamEvent> currentSubscriber;
    synchronized (stateMonitor) {
      if (!isActiveLocked()) {
        return;
      }
      terminated = true;
      stateMonitor.notifyAll();
      currentSubscriber = subscriber;
    }
    synchronized (signalMonitor) {
      currentSubscriber.onComplete();
    }
  }

  private void fail(Throwable failure) {
    Flow.Subscriber<? super AgentStreamEvent> currentSubscriber;
    synchronized (stateMonitor) {
      if (!isActiveLocked()) {
        return;
      }
      terminated = true;
      stateMonitor.notifyAll();
      currentSubscriber = subscriber;
    }
    cancellationToken.cancel();
    synchronized (signalMonitor) {
      currentSubscriber.onError(failure);
    }
  }

  private void rejectDuplicateSubscriber(Flow.Subscriber<? super AgentStreamEvent> duplicateSubscriber) {
    duplicateSubscriber.onSubscribe(EmptySubscription.INSTANCE);
    duplicateSubscriber.onError(new IllegalStateException("Agent stream supports only one subscriber"));
  }

  private boolean isActiveLocked() {
    return subscribed && !cancelled && !terminated;
  }

  private boolean isTerminalEvent(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd
        || event instanceof AgentStreamEvent.AgentError;
  }

  private long addCap(long current, long increment) {
    long updated = current + increment;
    return updated < 0 ? Long.MAX_VALUE : updated;
  }

  private final class AgentStreamSubscription implements Flow.Subscription {

    @Override
    public void request(long count) {
      SingleSubscriberAgentStreamPublisher.this.request(count);
    }

    @Override
    public void cancel() {
      SingleSubscriberAgentStreamPublisher.this.cancel();
    }
  }

  private enum EmptySubscription implements Flow.Subscription {
    INSTANCE;

    @Override
    public void request(long count) {
    }

    @Override
    public void cancel() {
    }
  }
}
