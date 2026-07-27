package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 一个 OpenAI provider instance 版本共享的同步/流式 SDK client 包装。 */
final class OpenAiProviderClient implements LlmProviderClient {

  private static final LlmProviderId PROVIDER_ID = LlmProviderId.of(OpenAiProviderAdapter.PROVIDER_TYPE.value());

  private final OpenAiResponsesClient client;
  private final OpenAiResponsesMapper mapper;

  OpenAiProviderClient(OpenAiResponsesClient client) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.mapper = new OpenAiResponsesMapper(new ObjectMapper(), PROVIDER_ID);
  }

  @Override
  public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
    try {
      return mapper.toResult(client.create(mapper.toParams(request, upstreamModelId)));
    } catch (Throwable error) {
      throw OpenAiLlmExceptionMapper.map(error, PROVIDER_ID, upstreamModelId);
    }
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
    try {
      return new OpenAiStreamPublisher(
          client.createStreaming(mapper.toParams(request, upstreamModelId)), mapper, PROVIDER_ID, upstreamModelId);
    } catch (Throwable error) {
      throw OpenAiLlmExceptionMapper.map(error, PROVIDER_ID, upstreamModelId);
    }
  }
}
