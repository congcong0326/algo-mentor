package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * 单订阅者、同步投递的学习计划流事件出口。
 *
 * <p>生产者线程在下游声明 demand 后直接调用 Subscriber 回调。因此慢 SSE 客户端只会阻塞所属的
 * Agent run，不会把响应写入转交给 JVM 公共线程池。</p>
 */
public final class SingleSubscriberSynchronousPublisher<T> implements Flow.Publisher<T> {

  private final Object stateMonitor = new Object();
  private final Object signalMonitor = new Object();

  private Flow.Subscriber<? super T> subscriber;
  private boolean subscribed;
  private boolean cancelled;
  private boolean terminated;
  private long demand;

  @Override
  public void subscribe(Flow.Subscriber<? super T> nextSubscriber) {
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
      nextSubscriber.onSubscribe(new PublisherSubscription());
    } catch (RuntimeException subscriberFailure) {
      cancel();
      throw subscriberFailure;
    }
  }

  /**
   * 同步投递一个事件；下游尚未声明 demand 时阻塞当前生产线程。
   *
   * @return 下游已取消或流已经结束时返回 {@code false}
   */
  public boolean emit(T event) {
    Objects.requireNonNull(event, "event must not be null");
    Flow.Subscriber<? super T> currentSubscriber;
    synchronized (stateMonitor) {
      while (demand == 0 && isActiveLocked()) {
        try {
          stateMonitor.wait();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return false;
        }
      }
      if (!isActiveLocked()) {
        return false;
      }
      if (demand != Long.MAX_VALUE) {
        demand--;
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

  /** 完成流；重复调用和取消后的调用均为无操作。 */
  public void complete() {
    Flow.Subscriber<? super T> currentSubscriber;
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

  /** 以错误结束流；重复调用和取消后的调用均为无操作。 */
  public void fail(Throwable failure) {
    Objects.requireNonNull(failure, "failure must not be null");
    Flow.Subscriber<? super T> currentSubscriber;
    synchronized (stateMonitor) {
      if (!isActiveLocked()) {
        return;
      }
      terminated = true;
      stateMonitor.notifyAll();
      currentSubscriber = subscriber;
    }
    synchronized (signalMonitor) {
      currentSubscriber.onError(failure);
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
  }

  private void rejectDuplicateSubscriber(Flow.Subscriber<? super T> duplicateSubscriber) {
    duplicateSubscriber.onSubscribe(EmptySubscription.INSTANCE);
    duplicateSubscriber.onError(new IllegalStateException("Learning plan stream supports only one subscriber"));
  }

  private boolean isActiveLocked() {
    return subscribed && !cancelled && !terminated;
  }

  private long addCap(long current, long increment) {
    long updated = current + increment;
    return updated < 0 ? Long.MAX_VALUE : updated;
  }

  private final class PublisherSubscription implements Flow.Subscription {

    @Override
    public void request(long count) {
      SingleSubscriberSynchronousPublisher.this.request(count);
    }

    @Override
    public void cancel() {
      SingleSubscriberSynchronousPublisher.this.cancel();
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
