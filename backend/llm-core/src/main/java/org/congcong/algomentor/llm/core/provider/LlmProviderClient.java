package org.congcong.algomentor.llm.core.provider;

import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 一个 provider instance 当前版本的可复用 SDK Client。 */
public interface LlmProviderClient {

  LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request);

  Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request);
}
