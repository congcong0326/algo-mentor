package org.congcong.algomentor.llm.openai.compatible;

import java.net.URI;

/** 已通过 provider 严格配置校验后的通用 SDK 连接参数。 */
public record OpenAiCompatibleConnectionConfig(
    String apiKey,
    URI baseUrl,
    int timeoutSeconds,
    int maxRetries
) {

  public OpenAiCompatibleConnectionConfig {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key must not be blank");
    }
    apiKey = apiKey.trim();
    if (baseUrl == null || !baseUrl.isAbsolute()
        || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
      throw new IllegalArgumentException("Base URL must be an absolute HTTP(S) URI");
    }
    if (timeoutSeconds < 1) {
      throw new IllegalArgumentException("Timeout seconds must be positive");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("Max retries must not be negative");
    }
  }

  @Override
  public String toString() {
    return "OpenAiCompatibleConnectionConfig[apiKey=REDACTED, baseUrl=REDACTED, timeoutSeconds="
        + timeoutSeconds + ", maxRetries=" + maxRetries + "]";
  }
}
