package org.congcong.algomentor.llm.core;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultLlmGateway implements LlmGateway {

  private static final Set<LlmCapability> COMPLETION_CAPABILITIES = Set.of(LlmCapability.CHAT_COMPLETION);
  private final Map<LlmProviderId, LlmProvider> providers;
  private final LlmProviderId defaultProviderId;
  private final LlmModelId defaultModelId;

  public DefaultLlmGateway(List<LlmProvider> providers, LlmProviderId defaultProviderId, LlmModelId defaultModelId) {
    if (providers == null || providers.isEmpty()) {
      throw new IllegalArgumentException("LLM gateway providers must not be empty");
    }
    if (defaultProviderId == null) {
      throw new IllegalArgumentException("LLM gateway default provider id must not be null");
    }
    if (defaultModelId == null) {
      throw new IllegalArgumentException("LLM gateway default model id must not be null");
    }
    this.providers = providers.stream()
        .collect(Collectors.toUnmodifiableMap(LlmProvider::id, Function.identity()));
    this.defaultProviderId = defaultProviderId;
    this.defaultModelId = defaultModelId;
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    ResolvedRequest resolved = resolve(request);
    ensureSupported(resolved.descriptor(), requiredCapabilities(request, false));
    return resolved.provider().complete(resolved.request());
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    ResolvedRequest resolved = resolve(request);
    ensureSupported(resolved.descriptor(), requiredCapabilities(request, true));
    return resolved.provider().stream(resolved.request());
  }

  private ResolvedRequest resolve(LlmCompletionRequest request) {
    if (request == null) {
      throw new LlmException(LlmErrorCode.INVALID_REQUEST, "LLM request must not be null");
    }
    LlmProviderId providerId = request.modelSelector().providerId().orElse(defaultProviderId);
    LlmModelId modelId = request.modelSelector().modelId().orElse(defaultModelId);
    LlmProvider provider = providers.get(providerId);
    if (provider == null) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "Unknown LLM provider: " + providerId.value(),
          providerId,
          modelId,
          false,
          Map.of(),
          null);
    }
    LlmModelDescriptor descriptor = provider.capabilities().models().get(modelId.value());
    if (descriptor == null) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "Unknown LLM model: " + modelId.value(),
          providerId,
          modelId,
          false,
          Map.of(),
          null);
    }
    return new ResolvedRequest(
        provider,
        descriptor,
        request.withModelSelector(LlmModelSelector.of(provider.id(), modelId)));
  }

  private Set<LlmCapability> requiredCapabilities(LlmCompletionRequest request, boolean streaming) {
    EnumSet<LlmCapability> capabilities = EnumSet.copyOf(COMPLETION_CAPABILITIES);
    capabilities.addAll(request.modelSelector().requiredCapabilities());
    if (requiresToolCalling(request)) {
      capabilities.add(LlmCapability.TOOL_CALLING);
    }
    if (request.responseFormat() instanceof LlmResponseFormat.JsonObject) {
      capabilities.add(LlmCapability.STRUCTURED_OUTPUT);
    }
    if (request.responseFormat() instanceof LlmResponseFormat.JsonSchema) {
      capabilities.add(LlmCapability.JSON_SCHEMA_OUTPUT);
    }
    if (requiresVisionInput(request)) {
      capabilities.add(LlmCapability.VISION_INPUT);
    }
    if (streaming) {
      capabilities.add(LlmCapability.STREAMING);
    }
    return Set.copyOf(capabilities);
  }

  private boolean requiresToolCalling(LlmCompletionRequest request) {
    return !request.tools().isEmpty()
        || request.toolChoice().mode() == LlmToolChoice.Mode.REQUIRED
        || request.toolChoice().mode() == LlmToolChoice.Mode.SPECIFIC;
  }

  private boolean requiresVisionInput(LlmCompletionRequest request) {
    return request.messages().stream()
        .flatMap(message -> message.content().stream())
        .anyMatch(LlmContentPart.Image.class::isInstance);
  }

  private void ensureSupported(LlmModelDescriptor descriptor, Set<LlmCapability> requiredCapabilities) {
    Set<LlmCapability> supportedCapabilities = descriptor.supportedCapabilities();
    Set<LlmCapability> unsupportedCapabilities = requiredCapabilities.stream()
        .filter(capability -> !supportedCapabilities.contains(capability))
        .collect(Collectors.toUnmodifiableSet());
    if (!unsupportedCapabilities.isEmpty()) {
      throw LlmException.unsupportedCapability(
          "LLM model %s does not support capabilities: %s".formatted(
              descriptor.modelId().value(),
              unsupportedCapabilities),
          descriptor.providerId(),
          descriptor.modelId());
    }
  }

  private record ResolvedRequest(
      LlmProvider provider,
      LlmModelDescriptor descriptor,
      LlmCompletionRequest request
  ) {
  }
}
