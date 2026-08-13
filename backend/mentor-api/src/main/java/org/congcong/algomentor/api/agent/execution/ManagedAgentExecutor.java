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
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.execution.AgentExecutionPermit;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.api.config.AgentExecutorProperties;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Spring 管理的 Agent loop 专用平台线程池。 */
public final class ManagedAgentExecutor implements AgentExecutor {

  private static final Logger log = LoggerFactory.getLogger(ManagedAgentExecutor.class);
  private static final ThreadLocal<ExecutionContext> EXECUTOR_CONTEXT = new ThreadLocal<>();

  private final ThreadPoolExecutor executor;
  private final AgentExecutionBulkheadRegistry bulkheadRegistry;
  private final Duration shutdownTimeout;
  private final Counter rejectedCounter;
  private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);

  public ManagedAgentExecutor(
      AgentExecutorProperties properties,
      AgentExecutionBulkheadRegistry bulkheadRegistry,
      MeterRegistry meterRegistry
  ) {
    Objects.requireNonNull(properties, "properties must not be null");
    this.bulkheadRegistry = Objects.requireNonNull(bulkheadRegistry, "Agent execution bulkhead registry must not be null");
    this.shutdownTimeout = properties.getShutdownTimeout();
    int maximumPoolSize = bulkheadRegistry.totalCapacity();
    this.executor = new ThreadPoolExecutor(
        Math.min(10, maximumPoolSize),
        maximumPoolSize,
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
  public void execute(AgentExecutionGroup group, Runnable task) {
    AgentExecutionGroup executionGroup = Objects.requireNonNull(group, "Agent execution group must not be null");
    Objects.requireNonNull(task, "task must not be null");
    if (executor.isShutdown()) {
      throw rejected(AgentExecutionRejectionReason.SHUTDOWN, executionGroup, null);
    }
    AgentExecutionPermit permit = bulkheadRegistry.tryAcquire(executionGroup)
        .orElseThrow(() -> rejected(AgentExecutionRejectionReason.GROUP_SATURATED, executionGroup, null));
    try {
      Runnable traceAwareTask = RequestTraceContext.wrap(task);
      executor.execute(() -> runInExecutorThread(executionGroup, traceAwareTask, permit));
    } catch (RejectedExecutionException rejected) {
      permit.close();
      AgentExecutionRejectionReason reason = executor.isShutdown()
          ? AgentExecutionRejectionReason.SHUTDOWN
          : AgentExecutionRejectionReason.SATURATED;
      throw rejected(reason, executionGroup, rejected);
    }
  }

  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  @Override
  public boolean inExecutorThread() {
    return EXECUTOR_CONTEXT.get() != null;
  }

  @Override
  public java.util.Optional<AgentExecutionGroup> currentExecutionGroup() {
    ExecutionContext context = EXECUTOR_CONTEXT.get();
    return context == null ? java.util.Optional.empty() : java.util.Optional.of(context.group());
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

  private AgentExecutionRejectedException rejected(
      AgentExecutionRejectionReason reason,
      AgentExecutionGroup group,
      RejectedExecutionException cause
  ) {
    if (rejectedCounter != null && reason != AgentExecutionRejectionReason.GROUP_SATURATED) {
      rejectedCounter.increment();
    }
    log.warn(
        "Agent executor rejected task. group={} reason={} active={} poolSize={} maximumPoolSize={}",
        group.code(),
        reason,
        executor.getActiveCount(),
        executor.getPoolSize(),
        executor.getMaximumPoolSize());
    return new AgentExecutionRejectedException(
        reason,
        group,
        "Agent executor rejected task: " + reason,
        cause);
  }

  private void runInExecutorThread(
      AgentExecutionGroup group,
      Runnable task,
      AgentExecutionPermit permit
  ) {
    ExecutionContext previous = EXECUTOR_CONTEXT.get();
    EXECUTOR_CONTEXT.set(new ExecutionContext(
        previous == null ? 1 : previous.depth() + 1,
        group));
    try {
      task.run();
    } finally {
      try {
        bulkheadRegistry.recordCompletion(group);
        permit.close();
      } finally {
        if (previous == null) {
          EXECUTOR_CONTEXT.remove();
        } else {
          EXECUTOR_CONTEXT.set(previous);
        }
      }
    }
  }

  private record ExecutionContext(int depth, AgentExecutionGroup group) {
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
