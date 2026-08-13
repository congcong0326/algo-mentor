package org.congcong.algomentor.queue.metrics;

import java.time.Duration;
import java.util.Set;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.runtime.QueueWorkerState;

/** 队列运行指标端口，禁止把消息 key 或 value 作为标签。 */
public interface QueueMetrics {

  QueueMetrics NOOP = new QueueMetrics() { };

  default void bindTopics(Set<String> topics, QueueMessageRepository repository) { }

  default void recordDispatch(String topic, QueueDispatchOutcome outcome) { }

  default void recordWorkerState(String topic, QueueWorkerState state) { }

  default void recordWorkerFailure(String topic) { }

  default void recordTerminalFailure(String topic) { }

  default void recordCleanup(int deletedCount, Duration duration) { }
}
