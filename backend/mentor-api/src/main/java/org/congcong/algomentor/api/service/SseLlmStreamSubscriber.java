package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseLlmStreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

  private final SseEmitter emitter;
  private final LlmStreamSseMapper mapper;
  private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  public SseLlmStreamSubscriber(SseEmitter emitter, LlmStreamSseMapper mapper) {
    this.emitter = emitter;
    this.mapper = mapper;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    /*
     * Flow 的订阅模型要求 Subscriber 主动向上游声明“我还能处理多少个事件”。
     * 这里每次只 request(1)，表示先要 1 个事件，等 onNext 成功写入 SSE 后再要下一个。
     * 这样可以让上游按 HTTP 写出速度推进，避免一次性把大量 token/工具事件压到内存里。
     */
    subscription.request(1);
  }

  @Override
  public void onNext(AgentStreamEvent event) {
    try {
      /*
       * Publisher 产出的是领域事件 AgentStreamEvent；浏览器需要的是 SSE 协议格式。
       * mapper.toSseEvent(...) 负责把领域事件映射为 event: xxx + data: {...}，
       * emitter.send(...) 则把该事件写到当前 HTTP text/event-stream 响应中。
       */
      emitter.send(mapper.toSseEvent(event));
      if (isTerminalEvent(event)) {
        /*
         * AgentRunEnd/AgentError 是业务终态事件。发送终态后主动 complete 响应并 cancel 订阅，
         * 防止上游继续推送事件，也让前端能明确感知本次 SSE 流结束。
         */
        terminalEventSent.set(true);
        emitter.complete();
        cancel();
        return;
      }
      // 当前事件已经成功写给客户端，再向上游申请下一个事件。
      subscription.request(1);
    } catch (IOException | RuntimeException sendFailure) {
      // 写 HTTP 响应失败通常意味着客户端断开或连接不可用，此时取消上游并结束 SSE。
      cancel();
      emitter.completeWithError(sendFailure);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    if (terminalEventSent.compareAndSet(false, true)) {
      try {
        /*
         * 上游以异常形式结束时，也转换成统一的 agent_error SSE 事件。
         * terminalEventSent 确保不会重复发送 AgentError/AgentRunEnd 两类终态事件。
         */
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentError("unknown", toAgentException(throwable))));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        emitter.completeWithError(sendFailure);
      }
    }
  }

  @Override
  public void onComplete() {
    if (terminalEventSent.compareAndSet(false, true)) {
      try {
        /*
         * 理想情况下 Agent loop 会先发布 AgentRunEnd 再完成 Publisher。
         * 如果上游只触发 onComplete 而没有业务终态事件，这里补一个兜底的 AgentRunEnd，
         * 避免前端只看到连接关闭却拿不到明确的 run 结束事件。
         */
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentRunEnd(
            "unknown",
            1,
            LlmFinishReason.UNKNOWN,
            null)));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        emitter.completeWithError(sendFailure);
      }
    }
  }

  public void cancel() {
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private boolean isTerminalEvent(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd || event instanceof AgentStreamEvent.AgentError;
  }

  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException agentException) {
      return agentException;
    }
    String message = throwable.getMessage() == null ? "Agent stream failed" : throwable.getMessage();
    return new AgentException(AgentErrorCode.UNKNOWN, message, false, null, throwable);
  }
}
