package org.congcong.algomentor.llm.core.gateway;

import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/**
 * 用于同步及流式 LLM 补全的网关契约。
 */
public interface LlmGateway {

  /**
   *   complete(LlmCompletionRequest request)：同步/非流式调用。
   *   调用方等模型完整生成结束后，一次性拿到 LlmCompletionResult，里面包含最终消息、工具调用、结构化输出、finish reason、usage、provider/model 等完整结果。适合后台生成、
   *   摘要、题解生成、结构化 JSON 输出等不需要边生成边展示的场景。
   * @param request
   * @return
   */
  LlmCompletionResult complete(LlmCompletionRequest request);

  /**
   *   stream(LlmCompletionRequest request)：流式调用。
   *   返回 Flow.Publisher<LlmStreamEvent>，调用方订阅后陆续收到事件，比如 MessageStart、ContentDelta、ToolCallStart、ToolCallDelta、MessageEnd、Usage、Error、Heartbeat。适合 SSE、
   *   聊天窗口、AI 讲解逐字输出这类需要低延迟反馈的场景。
   * @param request
   * @return
   */
  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
