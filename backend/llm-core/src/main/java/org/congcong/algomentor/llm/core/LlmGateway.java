package org.congcong.algomentor.llm.core;

import java.util.concurrent.Flow;

public interface LlmGateway {

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
