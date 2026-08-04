package org.congcong.algomentor.llm.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

class DeepSeekProviderConfigTest {

  @Test
  void parsesOnlyTheStrictSupportedConfiguration() {
    var config = JsonNodeFactory.instance.objectNode()
        .put("apiKey", "deepseek-test-key")
        .put("baseUrl", "https://gateway.example.test")
        .put("timeoutSeconds", 45)
        .put("maxRetries", 1);

    DeepSeekProviderConfig parsed = DeepSeekProviderConfig.fromJson(config);

    assertThat(parsed.toConnectionConfig().toString()).doesNotContain("deepseek-test-key", "gateway.example.test");
    assertThat(parsed.toString()).doesNotContain("deepseek-test-key", "gateway.example.test");
    assertThatThrownBy(() -> DeepSeekProviderConfig.fromJson(config.deepCopy().put("unknown", true)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported field");
  }

  @Test
  void rejectsInvalidSecretsUrlsTimeoutsAndRetries() {
    var config = JsonNodeFactory.instance.objectNode()
        .put("apiKey", "key")
        .put("baseUrl", "https://api.deepseek.com")
        .put("timeoutSeconds", 1)
        .put("maxRetries", 0);

    assertThatThrownBy(() -> DeepSeekProviderConfig.fromJson(config.deepCopy().put("apiKey", " ")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeepSeekProviderConfig.fromJson(config.deepCopy().put("baseUrl", "not-a-url")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeepSeekProviderConfig.fromJson(config.deepCopy().put("timeoutSeconds", 0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DeepSeekProviderConfig.fromJson(config.deepCopy().put("maxRetries", -1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
