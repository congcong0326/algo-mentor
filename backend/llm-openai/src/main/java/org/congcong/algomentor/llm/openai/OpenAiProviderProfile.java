package org.congcong.algomentor.llm.openai;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.openai.compatible.OpenAiCompatibleProviderProfile;

/** OpenAI 在共享 Responses 传输层中的协议描述。 */
public final class OpenAiProviderProfile implements OpenAiCompatibleProviderProfile {

  public static final LlmProviderType PROVIDER_TYPE = LlmProviderType.of("openai");
  public static final OpenAiProviderProfile INSTANCE = new OpenAiProviderProfile();

  private static final Set<LlmCapability> SUPPORTED_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.REASONING_EFFORT,
      LlmCapability.TOKEN_USAGE,
      LlmCapability.CACHED_TOKEN_USAGE);
  private static final Set<LlmReasoningEffort> ACCEPTED_REASONING_EFFORTS =
      Collections.unmodifiableSet(EnumSet.allOf(LlmReasoningEffort.class));

  private OpenAiProviderProfile() {
  }

  @Override
  public LlmProviderType providerType() {
    return PROVIDER_TYPE;
  }

  @Override
  public String displayName() {
    return "OpenAI";
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
      throw LlmException.unsupportedCapability(
          "OpenAI does not accept reasoning effort " + effort.wireValue(),
          providerId(),
          modelId);
    }
  }

  @Override
  public boolean requiresReasoningContinuationForToolCalls() {
    return false;
  }
}
