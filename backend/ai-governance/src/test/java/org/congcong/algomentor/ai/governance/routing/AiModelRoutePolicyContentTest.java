package org.congcong.algomentor.ai.governance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.junit.jupiter.api.Test;

class AiModelRoutePolicyContentTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void readsLegacyRouteContentAndPreservesExplicitReasoningValues() throws Exception {
    AiModelRoutePolicyContent legacy = objectMapper.readValue(
        "{\"modelId\":101}", AiModelRoutePolicyContent.class);
    AiModelRoutePolicyContent none = new AiModelRoutePolicyContent(101L, LlmReasoningEffort.NONE);

    assertThat(legacy.reasoningEffort()).isNull();
    assertThat(objectMapper.writeValueAsString(legacy)).isEqualTo("{\"modelId\":101}");
    assertThat(objectMapper.readTree(objectMapper.writeValueAsString(none)))
        .isEqualTo(objectMapper.readTree("{\"modelId\":101,\"reasoningEffort\":\"none\"}"));
    assertThat(objectMapper.readValue("{\"modelId\":101,\"reasoningEffort\":\"max\"}",
        AiModelRoutePolicyContent.class).reasoningEffort()).isEqualTo(LlmReasoningEffort.MAX);
  }

  @Test
  void rejectsUnknownReasoningEffort() {
    assertThatThrownBy(() -> objectMapper.readValue(
        "{\"modelId\":101,\"reasoningEffort\":\"unsupported\"}", AiModelRoutePolicyContent.class))
        .isInstanceOf(Exception.class);
  }
}
