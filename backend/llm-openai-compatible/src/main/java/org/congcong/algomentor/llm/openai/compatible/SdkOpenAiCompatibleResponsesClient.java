package org.congcong.algomentor.llm.openai.compatible;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SdkOpenAiCompatibleResponsesClient implements OpenAiCompatibleResponsesClient {

  private static final Logger log = LoggerFactory.getLogger(SdkOpenAiCompatibleResponsesClient.class);

  private final OpenAIClient client;
  private final OpenAIClient streamingClient;

  public SdkOpenAiCompatibleResponsesClient(OpenAIClient client) {
    this(client, client);
  }

  public SdkOpenAiCompatibleResponsesClient(OpenAIClient client, OpenAIClient streamingClient) {
    this.client = Objects.requireNonNull(client, "client must not be null");
    this.streamingClient = Objects.requireNonNull(streamingClient, "streamingClient must not be null");
  }

  @Override
  public Response create(ResponseCreateParams params) {
    Instant startedAt = Instant.now();
    log.info("OpenAI-compatible SDK responses.create started.");
    try {
      Response response = client.responses().create(params);
      log.info(
          "OpenAI-compatible SDK responses.create completed. responseId={} status={} elapsedMs={}",
          response.id(),
          response.status(),
          Duration.between(startedAt, Instant.now()).toMillis());
      return response;
    } catch (RuntimeException exception) {
      log.warn(
          "OpenAI-compatible SDK responses.create failed. elapsedMs={} exceptionType={}",
          Duration.between(startedAt, Instant.now()).toMillis(),
          exception.getClass().getName());
      throw exception;
    }
  }

  @Override
  public StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params) {
    return streamingClient.responses().createStreaming(params);
  }

  static SdkOpenAiCompatibleResponsesClient create(
      OpenAiCompatibleConnectionConfig config,
      Duration timeout,
      Duration streamingTimeout
  ) {
    Objects.requireNonNull(config, "config must not be null");
    validateTimeout(timeout, "timeout");
    validateTimeout(streamingTimeout, "streamingTimeout");
    OpenAIClient client = OpenAIOkHttpClient.builder()
        .apiKey(config.apiKey())
        .baseUrl(config.baseUrl().toString())
        .timeout(timeout)
        .maxRetries(config.maxRetries())
        .build();
    OpenAIClient streamingClient = OpenAIOkHttpClient.builder()
        .apiKey(config.apiKey())
        .baseUrl(config.baseUrl().toString())
        .timeout(streamingTimeout)
        .maxRetries(config.maxRetries())
        .build();
    return new SdkOpenAiCompatibleResponsesClient(client, streamingClient);
  }

  private static void validateTimeout(Duration timeout, String fieldName) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
  }
}
