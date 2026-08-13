package org.congcong.algomentor.queue.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 无外部告警集成时的默认告警出口，便于由日志平台配置高优先级告警规则。 */
public final class LoggingQueueAlertNotifier implements QueueAlertNotifier {

  private static final Logger log = LoggerFactory.getLogger(LoggingQueueAlertNotifier.class);

  @Override
  public void notifyTerminalFailure(QueueTerminalFailure failure) {
    log.error("Persistent queue topic stopped after terminal delivery failure. topic={} messageCount={} attempts={} errorType={}",
        failure.topic(), failure.messageCount(), failure.attempts(), failure.errorType());
  }
}
