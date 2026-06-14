package org.congcong.algomentor.llm.core.gateway;

import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/**
 * 用于同步及流式 LLM 补全的网关契约。
 */
public interface LlmGateway {

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
