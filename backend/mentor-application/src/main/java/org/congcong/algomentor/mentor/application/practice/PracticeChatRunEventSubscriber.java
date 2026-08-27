package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.exception.LlmException;

/**
 * Practice Chat 单次 run 的唯一事件出口订阅者。
 *
 * <p>存储失败是实时体验降级，必须继续向上游申请下一条事件，绝不能取消 Agent run。</p>
 */
public final class PracticeChatRunEventSubscriber implements Flow.Subscriber<AgentStreamEvent> {

  /** Redis Stream 的 run 级事件写入端口。 */
  public interface EventStore {
    void append(String runUuid, AgentStreamEvent event);
  }

  private final String runUuid;
  private final EventStore eventStore;
  private final Runnable runEnded;
  private final AtomicBoolean terminalEventReceived = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  public PracticeChatRunEventSubscriber(String runUuid, EventStore eventStore) {
    this(runUuid, eventStore, () -> {});
  }

  public PracticeChatRunEventSubscriber(String runUuid, EventStore eventStore, Runnable runEnded) {
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Practice chat run uuid must not be blank");
    }
    this.runUuid = runUuid;
    this.eventStore = Objects.requireNonNull(eventStore, "Practice chat event store must not be null");
    this.runEnded = Objects.requireNonNull(runEnded, "Practice chat run end callback must not be null");
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = Objects.requireNonNull(subscription, "Practice chat stream subscription must not be null");
    subscription.request(1);
  }

  @Override
  public void onNext(AgentStreamEvent event) {
    if (isTerminalEvent(event)) {
      terminalEventReceived.set(true);
    }
    try {
      eventStore.append(runUuid, Objects.requireNonNull(event, "Practice chat stream event must not be null"));
    } catch (RuntimeException ignored) {
      // EventStore 负责低敏日志和指标；此处必须保持 Agent 数据面可用。
    } finally {
      if (event instanceof AgentStreamEvent.AgentRunEnd) {
        try {
          runEnded.run();
        } catch (RuntimeException ignored) {
          // 会话时间戳只是展示辅助数据，不能改变 run 生命周期。
        }
      }
      if (subscription != null) {
        subscription.request(1);
      }
    }
  }

  @Override
  public void onError(Throwable throwable) {
    appendFallbackError(toAgentException(throwable));
  }

  @Override
  public void onComplete() {
    appendFallbackError(new AgentException(
        AgentErrorCode.UNKNOWN,
        "Agent stream completed without a terminal event",
        false,
        Map.of(),
        null));
  }

  private void appendFallbackError(AgentException error) {
    if (!terminalEventReceived.compareAndSet(false, true)) {
      return;
    }
    try {
      eventStore.append(runUuid, new AgentStreamEvent.AgentError(runUuid, error));
    } catch (RuntimeException ignored) {
      // EventStore 负责低敏日志和指标；此处必须保持 Agent 数据面可用。
    }
  }

  private boolean isTerminalEvent(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd || event instanceof AgentStreamEvent.AgentError;
  }

  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException error) {
      return error;
    }
    if (throwable instanceof LlmException error) {
      return new AgentException(
          AgentErrorCode.LLM_STREAM_FAILED,
          error.getMessage(),
          error.retryable(),
          error.metadata(),
          error);
    }
    String message = throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()
        ? "Agent stream failed"
        : throwable.getMessage();
    return new AgentException(AgentErrorCode.UNKNOWN, message, false, Map.of(), throwable);
  }
}
