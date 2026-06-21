package org.congcong.algomentor.agent.core;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次 Agent run 的取消信号。
 */
public final class AgentCancellationToken {

  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<Flow.Subscription> llmSubscription = new AtomicReference<>();
  private final AtomicReference<Thread> worker = new AtomicReference<>();

  public void cancel() {
    if (!cancelled.compareAndSet(false, true)) {
      return;
    }
    Flow.Subscription subscription = llmSubscription.get();
    if (subscription != null) {
      subscription.cancel();
    }
    Thread thread = worker.get();
    if (thread != null) {
      thread.interrupt();
    }
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  void worker(Thread thread) {
    worker.set(thread);
    if (isCancelled() && thread != null) {
      thread.interrupt();
    }
  }

  void llmSubscription(Flow.Subscription subscription) {
    llmSubscription.set(subscription);
    if (isCancelled() && subscription != null) {
      subscription.cancel();
    }
  }

  void clearLlmSubscription(Flow.Subscription subscription) {
    llmSubscription.compareAndSet(subscription, null);
  }
}
