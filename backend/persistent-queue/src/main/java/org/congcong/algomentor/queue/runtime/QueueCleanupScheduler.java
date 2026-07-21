package org.congcong.algomentor.queue.runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 独立于 Spring 全局 scheduler 的固定间隔清理任务。 */
public class QueueCleanupScheduler {
  private static final Logger log = LoggerFactory.getLogger(QueueCleanupScheduler.class);
  private final QueueCleanupService cleanupService;
  private final PersistentQueueProperties.Cleanup properties;
  private ScheduledExecutorService executor;

  public QueueCleanupScheduler(QueueCleanupService cleanupService, PersistentQueueProperties.Cleanup properties) {
    this.cleanupService = cleanupService;
    this.properties = properties;
  }

  public synchronized void start() {
    if (executor != null) return;
    executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "persistent-queue-cleanup");
      thread.setDaemon(true);
      return thread;
    });
    executor.scheduleWithFixedDelay(this::runSafely, properties.getFixedDelay().toMillis(),
        properties.getFixedDelay().toMillis(), TimeUnit.MILLISECONDS);
  }

  public synchronized void stop() {
    if (executor == null) return;
    executor.shutdown();
    executor = null;
  }

  private void runSafely() {
    try {
      cleanupService.cleanupOnce();
    } catch (RuntimeException exception) {
      log.warn("Persistent queue cleanup failed and will retry later", exception);
    }
  }
}
