package org.congcong.algomentor.api.agent.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.api.config.AgentExecutorProperties;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.junit.jupiter.api.Test;

class ManagedAgentExecutorTest {

  @Test
  void usesConfirmedPoolShapeAndPropagatesTraceContext() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ManagedAgentExecutor executor = new ManagedAgentExecutor(new AgentExecutorProperties(), registry);
    AtomicReference<String> threadName = new AtomicReference<>();
    AtomicReference<String> requestId = new AtomicReference<>();
    CountDownLatch completed = new CountDownLatch(1);

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-agent-pool")) {
      executor.execute(() -> {
        threadName.set(Thread.currentThread().getName());
        requestId.set(RequestTraceContext.currentRequestId().orElse(null));
        completed.countDown();
      });
    }

    await(completed);
    ThreadPoolExecutor pool = executor.threadPoolExecutor();
    assertThat(pool.getCorePoolSize()).isEqualTo(20);
    assertThat(pool.getMaximumPoolSize()).isEqualTo(100);
    assertThat(pool.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60);
    assertThat(pool.allowsCoreThreadTimeOut()).isTrue();
    assertThat(pool.getQueue()).isInstanceOf(SynchronousQueue.class).isEmpty();
    assertThat(pool.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
    assertThat(threadName.get()).startsWith("agent-loop-");
    assertThat(requestId).hasValue("request-agent-pool");
    executor.shutdown();
    assertThat(registry.get(AgentExecutorMetrics.COMPLETED).functionCounter().count()).isEqualTo(1.0);
    assertThat(registry.get(AgentExecutorMetrics.QUEUE_SIZE).gauge().value()).isZero();
    assertThat(registry.get(AgentExecutorMetrics.REJECTED).counter().count()).isZero();
  }

  @Test
  void rejectsImmediatelyWhenMaximumPoolSizeIsBusy() {
    AgentExecutorProperties properties = singleThreadProperties();
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ManagedAgentExecutor executor = new ManagedAgentExecutor(properties, registry);
    CountDownLatch workerStarted = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);
    executor.execute(() -> {
      workerStarted.countDown();
      await(releaseWorker);
    });
    await(workerStarted);

    try {
      assertThatThrownBy(() -> executor.execute(() -> {
      }))
          .isInstanceOf(AgentExecutionRejectedException.class)
          .extracting(error -> ((AgentExecutionRejectedException) error).reason())
          .isEqualTo(AgentExecutionRejectionReason.SATURATED);
      assertThat(executor.threadPoolExecutor().getQueue()).isEmpty();
      assertThat(registry.get(AgentExecutorMetrics.REJECTED).counter().count()).isEqualTo(1.0);
    } finally {
      releaseWorker.countDown();
      executor.shutdown();
    }
  }

  @Test
  void distinguishesShutdownRejection() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    ManagedAgentExecutor executor = new ManagedAgentExecutor(singleThreadProperties(), registry);
    executor.shutdown();

    assertThatThrownBy(() -> executor.execute(() -> {
    }))
        .isInstanceOf(AgentExecutionRejectedException.class)
        .extracting(error -> ((AgentExecutionRejectedException) error).reason())
        .isEqualTo(AgentExecutionRejectionReason.SHUTDOWN);
    assertThat(registry.get(AgentExecutorMetrics.REJECTED).counter().count()).isEqualTo(1.0);
  }

  @Test
  void marksExecutorThreadsWithoutDependingOnTheirNamesAndRestoresTraceContext() throws Exception {
    ManagedAgentExecutor executor = new ManagedAgentExecutor(singleThreadProperties(), new SimpleMeterRegistry());
    AtomicReference<Boolean> markedDuringManagedTask = new AtomicReference<>();
    AtomicReference<String> tracedRequestId = new AtomicReference<>();
    AtomicReference<Boolean> rawTaskMarked = new AtomicReference<>();
    AtomicReference<String> rawTaskRequestId = new AtomicReference<>();
    AtomicReference<Boolean> renamedWorkerStillMarked = new AtomicReference<>();
    CountDownLatch firstManagedTaskDone = new CountDownLatch(1);
    CountDownLatch secondManagedTaskDone = new CountDownLatch(1);

    try {
      try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-executor-marker")) {
        executor.execute(() -> {
          markedDuringManagedTask.set(executor.inExecutorThread());
          tracedRequestId.set(RequestTraceContext.currentRequestId().orElse(null));
          Thread.currentThread().setName("renamed-agent-worker");
          firstManagedTaskDone.countDown();
        });
      }
      await(firstManagedTaskDone);
      awaitIdle(executor.threadPoolExecutor());

      Future<?> rawTask = submitRaw(executor.threadPoolExecutor(), () -> {
        rawTaskMarked.set(executor.inExecutorThread());
        rawTaskRequestId.set(RequestTraceContext.currentRequestId().orElse(null));
      });
      rawTask.get(5, TimeUnit.SECONDS);

      executor.execute(() -> {
        renamedWorkerStillMarked.set(executor.inExecutorThread());
        secondManagedTaskDone.countDown();
      });
      await(secondManagedTaskDone);

      assertThat(markedDuringManagedTask).hasValue(true);
      assertThat(tracedRequestId).hasValue("request-executor-marker");
      assertThat(rawTaskMarked).hasValue(false);
      assertThat(rawTaskRequestId).hasValue(null);
      assertThat(renamedWorkerStillMarked).hasValue(true);
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void clearsExecutorThreadMarkerWhenTaskFails() {
    ManagedAgentExecutor executor = new ManagedAgentExecutor(singleThreadProperties(), new SimpleMeterRegistry());
    CountDownLatch failedTaskEntered = new CountDownLatch(1);
    AtomicReference<Boolean> rawTaskMarked = new AtomicReference<>();

    try {
      executor.execute(() -> {
        failedTaskEntered.countDown();
        throw new IllegalStateException("expected task failure");
      });
      await(failedTaskEntered);
      awaitIdle(executor.threadPoolExecutor());

      Future<?> rawTask = submitRaw(
          executor.threadPoolExecutor(),
          () -> rawTaskMarked.set(executor.inExecutorThread()));
      rawTask.get(5, TimeUnit.SECONDS);

      assertThat(rawTaskMarked).hasValue(false);
    } catch (Exception exception) {
      throw new AssertionError(exception);
    } finally {
      executor.shutdown();
    }
  }

  private static AgentExecutorProperties singleThreadProperties() {
    AgentExecutorProperties properties = new AgentExecutorProperties();
    properties.setCorePoolSize(1);
    properties.setMaxPoolSize(1);
    properties.setShutdownTimeout(java.time.Duration.ofSeconds(1));
    return properties;
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private static void awaitIdle(ThreadPoolExecutor executor) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (executor.getActiveCount() > 0 && System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
    assertThat(executor.getActiveCount()).isZero();
  }

  private static Future<?> submitRaw(ThreadPoolExecutor executor, Runnable task) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    RejectedExecutionException lastFailure = null;
    while (System.nanoTime() < deadline) {
      try {
        return executor.submit(task);
      } catch (RejectedExecutionException rejected) {
        lastFailure = rejected;
        Thread.onSpinWait();
      }
    }
    throw lastFailure == null ? new IllegalStateException("Raw agent executor task was not submitted") : lastFailure;
  }
}
