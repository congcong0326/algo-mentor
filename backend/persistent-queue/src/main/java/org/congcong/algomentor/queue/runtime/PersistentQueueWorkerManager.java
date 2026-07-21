package org.congcong.algomentor.queue.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.springframework.context.SmartLifecycle;

/** 仅在显式启用的单消费节点上启动每 topic 一个串行 worker。 */
public class PersistentQueueWorkerManager implements SmartLifecycle {
  private final QueueConsumerRegistry registry;
  private final QueueDispatcher dispatcher;
  private final PersistentQueueProperties.Consumer consumerProperties;
  private final QueueMetrics metrics;
  private final QueueMessageRepository repository;
  private final QueueCleanupScheduler cleanupScheduler;
  private final Map<String, QueueTopicWorker> workers = new LinkedHashMap<>();
  private final Map<String, ExecutorService> executors = new LinkedHashMap<>();
  private volatile boolean running;

  public PersistentQueueWorkerManager(
      QueueConsumerRegistry registry,
      QueueDispatcher dispatcher,
      PersistentQueueProperties.Consumer consumerProperties,
      QueueMetrics metrics,
      QueueMessageRepository repository,
      QueueCleanupScheduler cleanupScheduler) {
    this.registry = registry;
    this.dispatcher = dispatcher;
    this.consumerProperties = consumerProperties;
    this.metrics = metrics;
    this.repository = repository;
    this.cleanupScheduler = cleanupScheduler;
  }

  @Override
  public synchronized void start() {
    if (running) return;
    metrics.bindTopics(registry.topics(), repository);
    for (String topic : registry.topics()) {
      QueueTopicWorker worker = new QueueTopicWorker(topic, dispatcher, consumerProperties, metrics);
      ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "persistent-queue-" + topic);
        thread.setDaemon(true);
        return thread;
      });
      workers.put(topic, worker);
      executors.put(topic, executor);
      executor.execute(worker);
    }
    cleanupScheduler.start();
    running = true;
  }

  @Override
  public synchronized void stop() {
    if (!running) return;
    workers.values().forEach(QueueTopicWorker::stop);
    long timeoutMillis = consumerProperties.getShutdownTimeout().toMillis();
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    for (ExecutorService executor : executors.values()) {
      executor.shutdown();
      long remainingMillis = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
      try {
        if (!executor.awaitTermination(remainingMillis, TimeUnit.MILLISECONDS)) executor.shutdownNow();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }
    cleanupScheduler.stop();
    workers.clear();
    executors.clear();
    running = false;
  }

  @Override
  public boolean isRunning() { return running; }

  @Override
  public boolean isAutoStartup() { return true; }

  @Override
  public int getPhase() { return Integer.MAX_VALUE - 100; }
}
