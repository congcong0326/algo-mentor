package org.congcong.algomentor.api.agent.execution;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.api.config.AgentExecutorProperties;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spring 管理的 Agent loop 专用平台线程池。 */
public final class ManagedAgentExecutor implements AgentExecutor {

  private static final Logger log = LoggerFactory.getLogger(ManagedAgentExecutor.class);
  private static final ThreadLocal<Integer> EXECUTOR_DEPTH = new ThreadLocal<>();

  private final ThreadPoolExecutor executor;
  private final Duration shutdownTimeout;
  private final Counter rejectedCounter;
  private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

  public ManagedAgentExecutor(AgentExecutorProperties properties, MeterRegistry meterRegistry) {
    Objects.requireNonNull(properties, "properties must not be null");
    this.shutdownTimeout = properties.getShutdownTimeout();
    this.executor = new ThreadPoolExecutor(
        properties.getCorePoolSize(),
        properties.getMaxPoolSize(),
        properties.getKeepAlive().toMillis(),
        TimeUnit.MILLISECONDS,
        new SynchronousQueue<>(),
        new NamedThreadFactory(properties.getThreadNamePrefix()),
        new ThreadPoolExecutor.AbortPolicy());
    this.executor.allowCoreThreadTimeOut(true);
    this.rejectedCounter = meterRegistry == null
        ? null
        : Counter.builder(AgentExecutorMetrics.REJECTED)
            .description("Rejected Agent executor tasks")
            .register(meterRegistry);
    if (meterRegistry != null) {
      registerMetrics(meterRegistry);
    }
  }

  @Override
  public void execute(Runnable task) {
    Objects.requireNonNull(task, "task must not be null");
    try {
      Runnable traceAwareTask = RequestTraceContext.wrap(task);
      executor.execute(() -> runInExecutorThread(traceAwareTask));
    } catch (RejectedExecutionException rejected) {
      if (rejectedCounter != null) {
        rejectedCounter.increment();
      }
      AgentExecutionRejectionReason reason = executor.isShutdown()
          ? AgentExecutionRejectionReason.SHUTDOWN
          : AgentExecutionRejectionReason.SATURATED;
      log.warn(
          "Agent executor rejected task. reason={} active={} poolSize={} maximumPoolSize={}",
          reason,
          executor.getActiveCount(),
          executor.getPoolSize(),
          executor.getMaximumPoolSize());
      throw new AgentExecutionRejectedException(
          reason,
          "Agent executor rejected task: " + reason,
          rejected);
    }
  }

  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  @Override
  public boolean inExecutorThread() {
    return EXECUTOR_DEPTH.get() != null;
  }

  /** 停止接收新任务，等待运行中任务结束，超时后中断剩余任务。 */
  public void shutdown() {
    if (!shutdownStarted.compareAndSet(false, true)) {
      return;
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        int cancelledTasks = executor.shutdownNow().size();
        log.warn(
            "Agent executor shutdown timed out. active={} cancelledQueuedTasks={}",
            executor.getActiveCount(),
            cancelledTasks);
        executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException interrupted) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  ThreadPoolExecutor threadPoolExecutor() {
    return executor;
  }

  private void registerMetrics(MeterRegistry meterRegistry) {
    Gauge.builder(AgentExecutorMetrics.ACTIVE, executor, ThreadPoolExecutor::getActiveCount)
        .description("Active Agent executor tasks")
        .register(meterRegistry);
    Gauge.builder(AgentExecutorMetrics.POOL_SIZE, executor, ThreadPoolExecutor::getPoolSize)
        .description("Current Agent executor pool size")
        .register(meterRegistry);
    FunctionCounter.builder(AgentExecutorMetrics.COMPLETED, executor, ThreadPoolExecutor::getCompletedTaskCount)
        .description("Completed Agent executor tasks")
        .register(meterRegistry);
    Gauge.builder(AgentExecutorMetrics.QUEUE_SIZE, executor, value -> value.getQueue().size())
        .description("Queued Agent executor tasks")
        .register(meterRegistry);
  }

  private static void runInExecutorThread(Runnable task) {
    Integer depth = EXECUTOR_DEPTH.get();
    EXECUTOR_DEPTH.set(depth == null ? 1 : depth + 1);
    try {
      task.run();
    } finally {
      if (depth == null) {
        EXECUTOR_DEPTH.remove();
      } else {
        EXECUTOR_DEPTH.set(depth);
      }
    }
  }

  private static final class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger sequence = new AtomicInteger(1);

    private NamedThreadFactory(String prefix) {
      this.prefix = Objects.requireNonNull(prefix, "thread name prefix must not be null");
    }

    @Override
    public Thread newThread(Runnable task) {
      return new Thread(task, prefix + sequence.getAndIncrement());
    }
  }
}
