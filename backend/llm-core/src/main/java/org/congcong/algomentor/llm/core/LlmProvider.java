package org.congcong.algomentor.llm.core;

import java.util.List;
import java.util.concurrent.Flow;

public interface LlmProvider {

  LlmProviderId id();

  LlmProviderCapabilities capabilities();

  List<LlmModelDescriptor> models();

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
