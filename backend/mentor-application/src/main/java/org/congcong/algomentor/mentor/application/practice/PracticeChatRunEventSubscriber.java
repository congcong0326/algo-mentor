package org.congcong.algomentor.mentor.application.practice;

import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;

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
    // Runtime 已持久化失败终态；Redis 写入端不需要反向改变 run 生命周期。
  }

  @Override
  public void onComplete() {
    // 终态事件由 Agent Loop 发出；无额外业务事件。
  }
}
