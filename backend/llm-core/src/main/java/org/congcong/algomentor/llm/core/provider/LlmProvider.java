package org.congcong.algomentor.llm.core.provider;

import java.util.List;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.model.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/**
 * 由 OpenAI 等具体 LLM 后端实现的提供商适配器契约。
 */
public interface LlmProvider {

  LlmProviderId id();

  LlmProviderCapabilities capabilities();

  List<LlmModelDescriptor> models();

  LlmCompletionResult complete(LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request);
}
