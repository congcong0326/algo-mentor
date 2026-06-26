package org.congcong.algomentor.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SdkOpenAiResponsesClient implements OpenAiResponsesClient {

  private static final Logger log = LoggerFactory.getLogger(SdkOpenAiResponsesClient.class);

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
    Instant startedAt = Instant.now();
    log.info("OpenAI SDK responses.create started.");
    try {
      Response response = client.responses().create(params);
      log.info(
          "OpenAI SDK responses.create completed. responseId={} status={} elapsedMs={}",
          response.id(),
          response.status(),
          Duration.between(startedAt, Instant.now()).toMillis());
      return response;
    } catch (RuntimeException exception) {
      log.warn(
          "OpenAI SDK responses.create failed. elapsedMs={} exceptionType={} message={}",
          Duration.between(startedAt, Instant.now()).toMillis(),
          exception.getClass().getName(),
          exception.getMessage());
      throw exception;
    }
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
    return streamingClient.responses().createStreaming(params);
  }
}
