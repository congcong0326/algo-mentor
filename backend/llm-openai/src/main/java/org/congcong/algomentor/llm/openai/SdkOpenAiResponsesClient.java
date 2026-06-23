package org.congcong.algomentor.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.Objects;

final class SdkOpenAiResponsesClient implements OpenAiResponsesClient {

  private final OpenAIClient client;
  private final OpenAIClient streamingClient;

  SdkOpenAiResponsesClient(OpenAIClient client) {
    this(client, client);
  }

  SdkOpenAiResponsesClient(OpenAIClient client, OpenAIClient streamingClient) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.streamingClient = Objects.requireNonNull(streamingClient, "streamingClient must not be null");
  }

  @Override
  public Response create(ResponseCreateParams params) {
    return client.responses().create(params);
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
    return streamingClient.responses().createStreaming(params);
  }
}
