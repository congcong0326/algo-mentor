package org.congcong.algomentor.llm.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;

/**
 * OpenAI SDK 的窄包装，避免 provider 与单元测试直接依赖完整 SDK client。
 */
public interface OpenAiResponsesClient {

  Response create(ResponseCreateParams params);

  StreamResponse<ResponseStreamEvent> createStreaming(ResponseCreateParams params);

  static OpenAiResponsesClient fromProperties(OpenAiLlmProperties properties) {
    properties.validate();
    OpenAIClient client = OpenAIOkHttpClient.builder()
        .apiKey(properties.getApiKey())
        .baseUrl(properties.getBaseUrl().toString())
        .timeout(properties.getTimeout())
        .maxRetries(properties.getMaxRetries())
        .build();
    return new SdkOpenAiResponsesClient(client);
  }
}
