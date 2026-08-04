package org.congcong.algomentor.llm.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Set;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleConnectionConfig;

/** DeepSeek provider instance 的固定 JSON 配置契约。 */
public record DeepSeekProviderConfig(
    String apiKey,
    URI baseUrl,
    int timeoutSeconds,
    int maxRetries
) {

  private static final Set<String> FIELDS = Set.of("apiKey", "baseUrl", "timeoutSeconds", "maxRetries");

  public DeepSeekProviderConfig {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("DeepSeek apiKey must not be blank");
    }
    apiKey = apiKey.trim();
    if (baseUrl == null || !baseUrl.isAbsolute()
        || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
      throw new IllegalArgumentException("DeepSeek baseUrl must be an absolute HTTP(S) URI");
    }
    if (timeoutSeconds < 1) {
      throw new IllegalArgumentException("DeepSeek timeoutSeconds must be positive");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("DeepSeek maxRetries must not be negative");
    }
  }

  public static DeepSeekProviderConfig fromJson(JsonNode config) {
    if (config == null || !config.isObject()) {
      throw new IllegalArgumentException("DeepSeek config must be a JSON object");
    }
    Iterator<String> names = config.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (!FIELDS.contains(name)) {
        throw new IllegalArgumentException("DeepSeek config contains an unsupported field: " + name);
      }
    }
    return new DeepSeekProviderConfig(
        requiredText(config, "apiKey"),
        uri(requiredText(config, "baseUrl")),
        requiredInt(config, "timeoutSeconds"),
        requiredInt(config, "maxRetries"));
  }

  public OpenAiCompatibleConnectionConfig toConnectionConfig() {
    return new OpenAiCompatibleConnectionConfig(apiKey, baseUrl, timeoutSeconds, maxRetries);
  }

  @Override
  public String toString() {
    return "DeepSeekProviderConfig[apiKey=REDACTED, baseUrl=REDACTED, timeoutSeconds="
        + timeoutSeconds + ", maxRetries=" + maxRetries + "]";
  }

  private static String requiredText(JsonNode config, String field) {
    JsonNode value = config.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("DeepSeek config " + field + " must be a non-blank string");
    }
    return value.asText();
  }

  private static int requiredInt(JsonNode config, String field) {
    JsonNode value = config.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw new IllegalArgumentException("DeepSeek config " + field + " must be an integer");
    }
    return value.intValue();
  }

  private static URI uri(String value) {
    try {
      return new URI(value);
    } catch (URISyntaxException exception) {
      throw new IllegalArgumentException("DeepSeek baseUrl must be a valid URI", exception);
    }
  }
}
