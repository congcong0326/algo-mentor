package org.congcong.algomentor.ai.governance.completion;

import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;

/** 用于不装配治理基础设施的独立测试与最小运行时的兼容实现。 */
public class AiPassthroughCompletionGateway implements AiCompletionGateway {

  private final LlmGateway delegate;

  public AiPassthroughCompletionGateway(LlmGateway delegate) {
    this.delegate = delegate;
  }

  @Override
  public boolean isAllowed(AiCompletionContext context) {
    return true;
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
    return delegate.complete(AiCompletionRequestEnricher.enrich(request, context, null));
  }
}
