package org.congcong.algomentor.queue.alert;

/** 队列终态失败的告警扩展点；生产环境可接入告警平台，默认实现保留高优先级日志和指标。 */
@FunctionalInterface
public interface QueueAlertNotifier {

  void notifyTerminalFailure(QueueTerminalFailure failure);
}
