package org.congcong.algomentor.llm.deepseek;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleProviderProfile;

/** DeepSeek Responses 协议差异与请求限制。 */
public final class DeepSeekProviderProfile implements OpenAiCompatibleProviderProfile {

  public static final LlmProviderType PROVIDER_TYPE = LlmProviderType.of("deepseek");
  public static final DeepSeekProviderProfile INSTANCE = new DeepSeekProviderProfile();

  private static final Set<LlmCapability> SUPPORTED_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.REASONING_EFFORT,
      LlmCapability.TOKEN_USAGE,
      LlmCapability.CACHED_TOKEN_USAGE);
  private static final Set<LlmReasoningEffort> ACCEPTED_REASONING_EFFORTS = Collections.unmodifiableSet(
      EnumSet.of(LlmReasoningEffort.NONE, LlmReasoningEffort.LOW, LlmReasoningEffort.HIGH, LlmReasoningEffort.MAX));

  private DeepSeekProviderProfile() {
  }

  @Override
  public LlmProviderType providerType() {
    return PROVIDER_TYPE;
  }

  @Override
  public String displayName() {
    return "DeepSeek";
  }

  @Override
  public Set<LlmCapability> supportedCapabilities() {
    return SUPPORTED_CAPABILITIES;
  }

  @Override
  public Set<LlmReasoningEffort> acceptedReasoningEfforts() {
    return ACCEPTED_REASONING_EFFORTS;
  }

  @Override
  public void validateRequest(LlmModelId modelId, LlmCompletionRequest request) {
    LlmReasoningEffort effort = request.options().reasoningEffort();
    if (effort != null && !ACCEPTED_REASONING_EFFORTS.contains(effort)) {
      throw invalidRequest(modelId, "DeepSeek does not accept reasoning effort " + effort.wireValue());
    }
    if (effort != LlmReasoningEffort.NONE
        && (request.options().temperature() != null || request.options().topP() != null)) {
      throw invalidRequest(modelId, "DeepSeek reasoning requests do not accept temperature or topP");
    }
    boolean hasUnsupportedInput = request.messages().stream()
        .flatMap(message -> message.content().stream())
        .anyMatch(part -> part instanceof LlmContentPart.Image || part instanceof LlmContentPart.File);
    if (hasUnsupportedInput) {
      throw LlmException.unsupportedCapability(
          "DeepSeek Responses does not support image or file input in this version", providerId(), modelId);
    }
  }

  @Override
  public boolean requiresReasoningContinuationForToolCalls() {
    return true;
  }

  private LlmException invalidRequest(LlmModelId modelId, String message) {
    return new LlmException(LlmErrorCode.INVALID_REQUEST, message, providerId(), modelId, false, java.util.Map.of(), null);
  }
}
