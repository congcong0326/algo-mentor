package org.congcong.algomentor.llm.openai.compatible;

import java.util.Set;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;

/** OpenAI-compatible provider 在共享传输层中的可变协议边界。 */
public interface OpenAiCompatibleProviderProfile {

  LlmProviderType providerType();

  String displayName();

  Set<LlmCapability> supportedCapabilities();

  Set<LlmReasoningEffort> acceptedReasoningEfforts();

  void validateRequest(LlmModelId modelId, LlmCompletionRequest request);

  boolean requiresReasoningContinuationForToolCalls();

  /** 返回可暴露给调用方的通用 provider 调用失败文案。 */
  default String safeProviderErrorMessage() {
    return displayName() + " provider call failed";
  }

  /** 返回可暴露给调用方的 HTTP 错误文案。 */
  default String safeProviderHttpErrorMessage(int statusCode) {
    return displayName() + " provider returned HTTP " + statusCode;
  }

  /** 返回可暴露给调用方的流式错误文案。 */
  default String safeStreamErrorMessage(String message) {
    return message == null || message.isBlank() ? displayName() + " stream returned an error" : message;
  }

  default LlmProviderId providerId() {
    return LlmProviderId.of(providerType().value());
  }
}
