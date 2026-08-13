package org.congcong.algomentor.agent.core;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import org.congcong.algomentor.agent.core.execution.AgentExecutionConstants;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;

/**
 * 单订阅者、同步投递的 Agent 事件出口。
 *
 * <p>工作线程只有在下游声明 demand 后才会继续投递，慢消费者因此会自然阻塞自己的 Agent run，
 * 不需要额外的异步缓冲或公共线程池。</p>
 */
public final class SingleSubscriberAgentStreamPublisher
    implements Flow.Publisher<AgentStreamEvent>, AgentStreamEventSink {

  private final Object stateMonitor = new Object();
  private final Object signalMonitor = new Object();
  private final AgentCancellationToken cancellationToken;
  private final AgentExecutor executor;
  private final AgentExecutionGroup executionGroup;
  private final boolean inlineExecution;
  private final Runnable beforeSubmission;
  private final Consumer<AgentStreamEventSink> workerTask;
  private final Consumer<Throwable> submissionFailureHandler;

  private Flow.Subscriber<? super AgentStreamEvent> subscriber;
  private boolean subscribed;
  private boolean cancelled;
  private boolean terminated;
  private boolean terminalEventEmitted;
  private long demand;

  public SingleSubscriberAgentStreamPublisher(
      AgentCancellationToken cancellationToken,
      AgentExecutor executor,
      Consumer<AgentStreamEventSink> workerTask,
      Consumer<Throwable> submissionFailureHandler
  ) {
    this(cancellationToken, executor, AgentExecutionGroup.PRACTICE, false, () -> {}, workerTask, submissionFailureHandler);
  }

  /**
   * 创建一个在 executor 提交前完成受信运行初始化的单订阅事件出口。
   *
   * <p>初始化失败和 executor 拒绝都会经过 {@code submissionFailureHandler}，用于释放已创建的
   * 外部治理租约或持久化资源。</p>
   */
  public SingleSubscriberAgentStreamPublisher(
      AgentCancellationToken cancellationToken,
      AgentExecutor executor,
      boolean inlineExecution,
      Runnable beforeSubmission,
      Consumer<AgentStreamEventSink> workerTask,
      Consumer<Throwable> submissionFailureHandler
  ) {
    this(
        cancellationToken,
        executor,
        AgentExecutionGroup.PRACTICE,
        inlineExecution,
        beforeSubmission,
        workerTask,
        submissionFailureHandler);
  }

  /** 创建携带受信 Definition 执行组的单订阅事件出口。 */
  public SingleSubscriberAgentStreamPublisher(
      AgentCancellationToken cancellationToken,
      AgentExecutor executor,
      AgentExecutionGroup executionGroup,
      boolean inlineExecution,
      Runnable beforeSubmission,
      Consumer<AgentStreamEventSink> workerTask,
      Consumer<Throwable> submissionFailureHandler
  ) {
    this.cancellationToken = Objects.requireNonNull(cancellationToken, "cancellationToken must not be null");
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.executionGroup = Objects.requireNonNull(executionGroup, "Agent execution group must not be null");
    this.inlineExecution = inlineExecution;
    this.beforeSubmission = Objects.requireNonNull(beforeSubmission, "before submission task must not be null");
    this.workerTask = Objects.requireNonNull(workerTask, "workerTask must not be null");
    this.submissionFailureHandler = Objects.requireNonNull(
        submissionFailureHandler,
        "submissionFailureHandler must not be null");
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
    try {
      beforeSubmission.run();
      if (inlineExecution) {
        runWorker();
      } else {
        executor.execute(executionGroup, this::runWorker);
      }
    } catch (RejectedExecutionException rejected) {
      handleSubmissionFailure(toAgentException(rejected));
    } catch (RuntimeException submissionFailure) {
      handleSubmissionFailure(submissionFailure);
    }
  }

  private void handleSubmissionFailure(Throwable failure) {
    try {
      submissionFailureHandler.accept(failure);
    } catch (RuntimeException handlerFailure) {
      if (handlerFailure != failure) {
        failure.addSuppressed(handlerFailure);
      }
    }
    fail(failure);
  }

  private void runWorker() {
    Thread worker = Thread.currentThread();
    cancellationToken.worker(worker);
    try {
      workerTask.accept(this);
      complete();
    } catch (Throwable failure) {
      fail(failure);
    } finally {
      cancellationToken.clearWorker(worker);
    }
  }

  private AgentException toAgentException(RejectedExecutionException rejected) {
    AgentExecutionRejectionReason reason = rejected instanceof AgentExecutionRejectedException executionRejected
        ? executionRejected.reason()
        : executor.isShutdown()
            ? AgentExecutionRejectionReason.SHUTDOWN
            : AgentExecutionRejectionReason.SATURATED;
    AgentErrorCode code = reason == AgentExecutionRejectionReason.SHUTDOWN
        ? AgentErrorCode.AGENT_EXECUTOR_SHUTDOWN
        : AgentErrorCode.AGENT_EXECUTOR_OVERLOADED;
    String message = reason == AgentExecutionRejectionReason.SHUTDOWN
        ? "Agent service is shutting down"
        : "Agent service is temporarily busy";
    java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    metadata.put(AgentExecutionConstants.REJECTION_REASON_METADATA_KEY, reason.name());
    if (rejected instanceof AgentExecutionRejectedException executionRejected
        && executionRejected.group() != null) {
      metadata.put(AgentExecutionConstants.EXECUTION_GROUP_METADATA_KEY, executionRejected.group().code());
    }
    return new AgentException(
        code,
        message,
        true,
        java.util.Map.copyOf(metadata),
        rejected);
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
