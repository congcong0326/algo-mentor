package org.congcong.algomentor.ai.governance.completion;

import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;

/** 业务直接调用模型时必须使用的治理边界。 */
public interface AiCompletionGateway {

  boolean isAllowed(AiCompletionContext context);

  LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context);
}
