package org.congcong.algomentor.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.Objects;

final class SdkOpenAiResponsesClient implements OpenAiResponsesClient {

  private final OpenAIClient client;

  SdkOpenAiResponsesClient(OpenAIClient client) {
    this.client = Objects.requireNonNull(client, "client must not be null");
  }

  @Override
  public Response create(ResponseCreateParams params) {
    return client.responses().create(params);
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
    return client.responses().createStreaming(params);
  }
}
