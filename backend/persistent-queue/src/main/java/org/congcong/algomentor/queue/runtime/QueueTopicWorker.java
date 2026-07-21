package org.congcong.algomentor.queue.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 单 topic 的连续派发循环，空轮次和异常时才进行有界等待。 */
public class QueueTopicWorker implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(QueueTopicWorker.class);
  private final String topic;
  private final QueueDispatcher dispatcher;
  private final PersistentQueueProperties.Consumer consumerProperties;
  private final QueueMetrics metrics;
  private final AtomicBoolean running = new AtomicBoolean(true);
  private volatile Thread thread;

  public QueueTopicWorker(
      String topic,
      QueueDispatcher dispatcher,
      PersistentQueueProperties.Consumer consumerProperties,
      QueueMetrics metrics) {
    this.topic = topic;
    this.dispatcher = dispatcher;
    this.consumerProperties = consumerProperties;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    thread = Thread.currentThread();
    transition(QueueWorkerState.STARTING);
    transition(QueueWorkerState.RUNNING);
    try {
      while (running.get()) {
        try {
          List<QueueDispatchOutcome> outcomes = dispatcher.dispatchRound(topic, running::get);
          outcomes.forEach(outcome -> metrics.recordDispatch(topic, outcome));
          if (outcomes.stream().noneMatch(outcome -> outcome != QueueDispatchOutcome.NO_ELIGIBLE_KEY)) {
            waitForPollInterval();
          }
        } catch (RuntimeException exception) {
          metrics.recordWorkerFailure(topic);
          transition(QueueWorkerState.FAILED_RETRYING);
          log.warn("Persistent queue worker failed and will retry: topic={}", topic, exception);
          waitForPollInterval();
          if (running.get()) transition(QueueWorkerState.RUNNING);
        }
      }
    } finally {
      transition(QueueWorkerState.STOPPED);
      thread = null;
    }
  }

  public void stop() {
    transition(QueueWorkerState.STOPPING);
    running.set(false);
    Thread current = thread;
    if (current != null) current.interrupt();
  }

  public boolean isRunning() { return running.get(); }

  private void waitForPollInterval() {
    long nanos = consumerProperties.getPollInterval().toNanos();
    if (nanos > 0L && running.get()) LockSupport.parkNanos(this, nanos);
    if (Thread.interrupted()) Thread.currentThread().interrupt();
  }

  private void transition(QueueWorkerState state) {
    metrics.recordWorkerState(topic, state);
  }
}
