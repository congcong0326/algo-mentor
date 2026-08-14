package org.congcong.algomentor.agent.core.runtime.api;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;

/**
 * 已完成持久化准备、尚未提交至执行器的一次流式 Agent run。
 *
 * <p>调用方可在提交 worker 前取得稳定的 task/run 身份，并通过 {@link #subscribe(Flow.Subscriber)}
 * 同步触发提交。若执行器拒绝，该调用会在返回前抛出异常且 Runtime 已收束持久化运行。</p>
 */
public interface AgentPreparedStream {

  long taskId();

  String runUuid();

  boolean idempotentReplay();

  void subscribe(Flow.Subscriber<? super AgentStreamEvent> subscriber);
}
