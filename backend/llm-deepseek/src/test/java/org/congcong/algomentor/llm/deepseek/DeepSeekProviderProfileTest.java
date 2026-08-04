package org.congcong.algomentor.llm.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.junit.jupiter.api.Test;

class DeepSeekProviderProfileTest {

  private static final LlmModelId MODEL_ID = LlmModelId.of("deepseek-test-model");

  @Test
  void exposesOnlyTheSupportedProviderContract() {
    assertThat(DeepSeekProviderProfile.INSTANCE.providerId().value()).isEqualTo("deepseek");
    assertThat(DeepSeekProviderProfile.INSTANCE.displayName()).isEqualTo("DeepSeek");
    assertThat(DeepSeekProviderProfile.INSTANCE.supportedCapabilities()).contains(
        LlmCapability.CHAT_COMPLETION,
        LlmCapability.TOOL_CALLING,
        LlmCapability.REASONING_EFFORT);
    assertThat(DeepSeekProviderProfile.INSTANCE.supportedCapabilities()).doesNotContain(
        LlmCapability.VISION_INPUT, LlmCapability.FILE_INPUT, LlmCapability.EMBEDDING);
    assertThat(DeepSeekProviderProfile.INSTANCE.acceptedReasoningEfforts()).containsExactlyInAnyOrder(
        LlmReasoningEffort.NONE, LlmReasoningEffort.LOW, LlmReasoningEffort.HIGH, LlmReasoningEffort.MAX);
    assertThat(DeepSeekProviderProfile.INSTANCE.requiresReasoningContinuationForToolCalls()).isTrue();
  }

  @Test
  void validatesEffortAndThinkingSamplingConstraintsBeforeTransport() {
    DeepSeekProviderProfile.INSTANCE.validateRequest(MODEL_ID, request(null, null, null));
    for (LlmReasoningEffort effort : List.of(LlmReasoningEffort.NONE, LlmReasoningEffort.LOW,
        LlmReasoningEffort.HIGH, LlmReasoningEffort.MAX)) {
      DeepSeekProviderProfile.INSTANCE.validateRequest(MODEL_ID, request(effort, null, null));
    }
    DeepSeekProviderProfile.INSTANCE.validateRequest(MODEL_ID, request(LlmReasoningEffort.NONE, 0.7, 0.8));

    assertInvalid(request(LlmReasoningEffort.MINIMAL, null, null));
    assertInvalid(request(null, 0.7, null));
    assertInvalid(request(LlmReasoningEffort.HIGH, null, 0.8));
  }

  private static void assertInvalid(LlmCompletionRequest request) {
    assertThatThrownBy(() -> DeepSeekProviderProfile.INSTANCE.validateRequest(MODEL_ID, request))
        .isInstanceOf(LlmException.class)
        .extracting("code")
        .isEqualTo(LlmErrorCode.INVALID_REQUEST);
  }

  private static LlmCompletionRequest request(LlmReasoningEffort effort, Double temperature, Double topP) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.of(DeepSeekProviderProfile.INSTANCE.providerId(), MODEL_ID))
        .messages(List.of(LlmMessage.user("hello")))
        .options(new LlmGenerationOptions(temperature, topP, null, List.of(), null, null, effort))
        .build();
  }
}
