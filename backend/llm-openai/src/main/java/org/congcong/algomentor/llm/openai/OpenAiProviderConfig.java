package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Set;

/** OpenAI provider instance 的固定 JSON 配置契约。 */
public record OpenAiProviderConfig(
    String apiKey,
    URI baseUrl,
    int timeoutSeconds,
    int maxRetries
) {

  private static final Set<String> FIELDS = Set.of("apiKey", "baseUrl", "timeoutSeconds", "maxRetries");

  public OpenAiProviderConfig {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("OpenAI apiKey must not be blank");
    }
    apiKey = apiKey.trim();
    if (baseUrl == null || !baseUrl.isAbsolute()
        || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
      throw new IllegalArgumentException("OpenAI baseUrl must be an absolute HTTP(S) URI");
    }
    if (timeoutSeconds < 1) {
      throw new IllegalArgumentException("OpenAI timeoutSeconds must be positive");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("OpenAI maxRetries must not be negative");
    }
  }

  public static OpenAiProviderConfig fromJson(JsonNode config) {
    if (config == null || !config.isObject()) {
      throw new IllegalArgumentException("OpenAI config must be a JSON object");
    }
    Iterator<String> names = config.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (!FIELDS.contains(name)) {
        throw new IllegalArgumentException("OpenAI config contains an unsupported field: " + name);
      }
    }
    return new OpenAiProviderConfig(
        requiredText(config, "apiKey"),
        uri(requiredText(config, "baseUrl")),
        requiredInt(config, "timeoutSeconds"),
        requiredInt(config, "maxRetries"));
  }

  @Override
  public String toString() {
    return "OpenAiProviderConfig[apiKey=REDACTED, baseUrl=REDACTED, timeoutSeconds="
        + timeoutSeconds + ", maxRetries=" + maxRetries + "]";
  }

  private static String requiredText(JsonNode config, String field) {
    JsonNode value = config.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("OpenAI config " + field + " must be a non-blank string");
    }
    return value.asText();
  }

  private static int requiredInt(JsonNode config, String field) {
    JsonNode value = config.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw new IllegalArgumentException("OpenAI config " + field + " must be an integer");
    }
    return value.intValue();
  }

  private static URI uri(String value) {
    try {
      return new URI(value);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("OpenAI baseUrl must be a valid URI", exception);
    }
  }
}
