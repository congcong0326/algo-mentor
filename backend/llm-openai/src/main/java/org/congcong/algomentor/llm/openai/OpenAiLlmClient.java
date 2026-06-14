package org.congcong.algomentor.llm.openai;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.LlmClient;
import org.congcong.algomentor.llm.core.LlmCapability;
import org.congcong.algomentor.llm.core.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.LlmCompletionResult;
import org.congcong.algomentor.llm.core.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.LlmModelId;
import org.congcong.algomentor.llm.core.LlmProvider;
import org.congcong.algomentor.llm.core.LlmProviderCapabilities;
import org.congcong.algomentor.llm.core.LlmProviderId;
import org.congcong.algomentor.llm.core.LlmRequest;
import org.congcong.algomentor.llm.core.LlmResponse;
import org.congcong.algomentor.llm.core.LlmStreamEvent;
import org.congcong.algomentor.llm.core.LlmStreamHandler;

public class OpenAiLlmClient implements LlmClient, LlmProvider {

  private static final LlmProviderId PROVIDER_ID = LlmProviderId.of("openai");
  private static final Set<LlmCapability> SUPPORTED_CAPABILITIES = Set.of(
      LlmCapability.CHAT_COMPLETION,
      LlmCapability.STREAMING,
      LlmCapability.TOOL_CALLING,
      LlmCapability.STRUCTURED_OUTPUT,
      LlmCapability.JSON_SCHEMA_OUTPUT,
      LlmCapability.TOKEN_USAGE);

  private final OpenAiLlmProperties properties;

  public OpenAiLlmClient(OpenAiLlmProperties properties) {
    this.properties = properties;
  }

  @Override
  public LlmProviderId id() {
    return PROVIDER_ID;
  }

  @Override
  public LlmProviderCapabilities capabilities() {
    LlmModelId modelId = LlmModelId.of(properties.getModel());
    LlmModelDescriptor model = new LlmModelDescriptor(
        PROVIDER_ID,
        modelId,
        modelId.value(),
        SUPPORTED_CAPABILITIES,
        0,
        0,
        LlmGenerationOptions.defaults(),
        Map.of());
    return new LlmProviderCapabilities(SUPPORTED_CAPABILITIES, Map.of(modelId.value(), model));
  }

  @Override
  public List<LlmModelDescriptor> models() {
    return List.copyOf(capabilities().models().values());
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    if (!properties.isEnabled()) {
      throw new IllegalStateException("OpenAI LLM provider is disabled");
    }
    throw new UnsupportedOperationException("OpenAI completion wiring is not implemented yet");
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    if (!properties.isEnabled()) {
      throw new IllegalStateException("OpenAI LLM provider is disabled");
    }
    throw new UnsupportedOperationException("OpenAI streaming wiring is not implemented yet");
  }

  @Override
  public LlmResponse complete(LlmRequest request) {
    LlmCompletionResult result = complete(request.toCompletionRequest(PROVIDER_ID));
    return new LlmResponse(result.message().text());
  }

  @Override
  public void stream(LlmRequest request, LlmStreamHandler handler) {
    if (!properties.isEnabled()) {
      handler.onError(new IllegalStateException("OpenAI LLM provider is disabled"));
      return;
    }
    handler.onError(new UnsupportedOperationException("OpenAI streaming wiring is not implemented yet"));
  }
}
