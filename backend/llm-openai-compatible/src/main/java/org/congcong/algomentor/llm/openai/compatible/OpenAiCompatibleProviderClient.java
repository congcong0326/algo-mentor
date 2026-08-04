package org.congcong.algomentor.llm.openai.compatible;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

/** 一个 provider instance 版本共享的同步/流式 SDK client 包装。 */
public final class OpenAiCompatibleProviderClient implements LlmProviderClient {

  private final OpenAiCompatibleResponsesClient client;
  private final OpenAiCompatibleResponsesMapper mapper;
  private final OpenAiCompatibleProviderProfile profile;

  public OpenAiCompatibleProviderClient(
      OpenAiCompatibleResponsesClient client,
      OpenAiCompatibleProviderProfile profile
  ) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.profile = Objects.requireNonNull(profile, "profile must not be null");
    this.mapper = new OpenAiCompatibleResponsesMapper(new ObjectMapper(), profile);
  }

  @Override
  public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
    try {
      profile.validateRequest(upstreamModelId, request);
      return mapper.toResult(client.create(mapper.toParams(request, upstreamModelId)));
    } catch (Throwable error) {
      throw OpenAiCompatibleExceptionMapper.map(error, profile, upstreamModelId);
    }
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmModelId upstreamModelId, LlmCompletionRequest request) {
    try {
      profile.validateRequest(upstreamModelId, request);
      return new OpenAiCompatibleStreamPublisher(
          client.createStreaming(mapper.toParams(request, upstreamModelId)), mapper, profile, upstreamModelId);
    } catch (Throwable error) {
      throw OpenAiCompatibleExceptionMapper.map(error, profile, upstreamModelId);
    }
  }
}
