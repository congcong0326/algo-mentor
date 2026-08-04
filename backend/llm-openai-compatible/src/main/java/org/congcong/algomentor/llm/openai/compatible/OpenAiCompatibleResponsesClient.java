package org.congcong.algomentor.llm.openai.compatible;

import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import java.time.Duration;

/**
 * OpenAI-compatible SDK 的窄包装，避免 provider 与单元测试直接依赖完整 SDK client。
 */
public interface OpenAiCompatibleResponsesClient {

  Response create(ResponseCreateParams params);

  StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params);

  static OpenAiCompatibleResponsesClient fromConnectionConfig(OpenAiCompatibleConnectionConfig config) {
    return fromConnectionConfig(
        config,
        Duration.ofSeconds(config.timeoutSeconds()),
        Duration.ofSeconds(config.timeoutSeconds()));
  }

  static OpenAiCompatibleResponsesClient fromConnectionConfig(
      OpenAiCompatibleConnectionConfig config,
      Duration timeout,
      Duration streamingTimeout
  ) {
    return SdkOpenAiCompatibleResponsesClient.create(config, timeout, streamingTimeout);
  }
}
