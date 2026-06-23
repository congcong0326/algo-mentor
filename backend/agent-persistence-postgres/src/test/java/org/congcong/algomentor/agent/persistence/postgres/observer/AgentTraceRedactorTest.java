package org.congcong.algomentor.agent.persistence.postgres.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentTraceRedactorTest {

  @Test
  void redactsCredentialHeadersTokensPasswordsAndSecretValues() {
    ObjectMapper mapper = new ObjectMapper();
    AgentTraceRedactor redactor = new AgentTraceRedactor(mapper);
    JsonNode input = mapper.valueToTree(Map.of(
        "Authorization", "Bearer abc.def.ghi",
        "openai_api_key", "sk-test",
        "oauthToken", "oauth-secret",
        "databasePassword", "db-secret",
        "headers", Map.of("Cookie", "SESSION=abc"),
        "normal", "keep"));

    JsonNode redacted = redactor.redact(input);

    assertThat(redacted.get("Authorization").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("openai_api_key").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("oauthToken").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("databasePassword").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("headers").get("Cookie").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("normal").asText()).isEqualTo("keep");
  }
}
