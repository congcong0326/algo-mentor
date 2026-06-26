package org.congcong.algomentor.llm.core.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelDescriptor;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProvider;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认网关实现，负责解析提供商/模型选择并在分发前检查能力。
 */
public class DefaultLlmGateway implements LlmGateway {

  private static final Logger log = LoggerFactory.getLogger(DefaultLlmGateway.class);
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
    validateDefaultSelection();
  }

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    Instant startedAt = Instant.now();
    ResolvedRequest resolved = resolve(request);
    Set<LlmCapability> requiredCapabilities = requiredCapabilities(request, false);
    ensureSupported(resolved.descriptor(), requiredCapabilities);
    log.info(
        "LLM gateway completion dispatch started. provider={} model={} purpose={} requiredCapabilities={} messageCount={} responseFormat={}",
        resolved.descriptor().providerId().value(),
        resolved.descriptor().modelId().value(),
        nullToEmpty(request.modelSelector().purpose()),
        requiredCapabilities,
        request.messages().size(),
        responseFormatName(request.responseFormat()));
    try {
      LlmCompletionResult result = resolved.provider().complete(resolved.request());
      log.info(
          "LLM gateway completion dispatch completed. provider={} model={} finishReason={} elapsedMs={}",
          result.provider().value(),
          result.model().value(),
          result.finishReason(),
          Duration.between(startedAt, Instant.now()).toMillis());
      return result;
    } catch (RuntimeException exception) {
      log.warn(
          "LLM gateway completion dispatch failed. provider={} model={} elapsedMs={} exceptionType={}",
          resolved.descriptor().providerId().value(),
          resolved.descriptor().modelId().value(),
          Duration.between(startedAt, Instant.now()).toMillis(),
          exception.getClass().getSimpleName());
      throw exception;
    }
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
        request.withModelSelector(request.modelSelector().withResolvedModel(provider.id(), modelId)));
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
    if (requiresFileInput(request)) {
      capabilities.add(LlmCapability.FILE_INPUT);
    }
    if (streaming) {
      capabilities.add(LlmCapability.STREAMING);
    }
    return Set.copyOf(capabilities);
  }

  private void validateDefaultSelection() {
    LlmProvider provider = providers.get(defaultProviderId);
    if (provider == null) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "Unknown default LLM provider: " + defaultProviderId.value(),
          defaultProviderId,
          defaultModelId,
          false,
          Map.of(),
          null);
    }
    if (!provider.capabilities().models().containsKey(defaultModelId.value())) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "Unknown default LLM model: " + defaultModelId.value(),
          defaultProviderId,
          defaultModelId,
          false,
          Map.of(),
          null);
    }
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

  private boolean requiresFileInput(LlmCompletionRequest request) {
    return request.messages().stream()
        .flatMap(message -> message.content().stream())
        .anyMatch(LlmContentPart.File.class::isInstance);
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

  private String responseFormatName(LlmResponseFormat responseFormat) {
    if (responseFormat instanceof LlmResponseFormat.JsonSchema schema) {
      return "JsonSchema:%s".formatted(schema.name());
    }
    return responseFormat.getClass().getSimpleName();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  /**
   * Request plus provider/model metadata after default selection has been resolved.
   */
  private record ResolvedRequest(
      LlmProvider provider,
      LlmModelDescriptor descriptor,
      LlmCompletionRequest request
  ) {
  }
}
