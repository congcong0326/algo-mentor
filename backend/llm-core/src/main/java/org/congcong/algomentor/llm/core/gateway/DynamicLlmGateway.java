package org.congcong.algomentor.llm.core.gateway;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffortResolver;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

/** 按业务执行已解析的动态调用目标分发，不包含全局默认 provider 或模型。 */
public class DynamicLlmGateway implements LlmGateway {

  private static final Set<LlmCapability> COMPLETION_CAPABILITIES = Set.of(LlmCapability.CHAT_COMPLETION);

  @Override
  public LlmCompletionResult complete(LlmCompletionRequest request) {
    LlmInvocationTarget target = requireTarget(request);
    LlmCompletionRequest effectiveRequest = LlmReasoningEffortResolver.apply(request);
    ensureSupported(target, requiredCapabilities(effectiveRequest, false));
    return target.client().complete(target.upstreamModelId(), effectiveRequest);
  }

  @Override
  public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
    LlmInvocationTarget target = requireTarget(request);
    LlmCompletionRequest effectiveRequest = LlmReasoningEffortResolver.apply(request);
    ensureSupported(target, requiredCapabilities(effectiveRequest, true));
    return target.client().stream(target.upstreamModelId(), effectiveRequest);
  }

  private LlmInvocationTarget requireTarget(LlmCompletionRequest request) {
    if (request == null) {
      throw new LlmException(LlmErrorCode.INVALID_REQUEST, "LLM request must not be null");
    }
    if (request.invocationTarget() == null) {
      throw new LlmException(
          LlmErrorCode.INVALID_REQUEST,
          "Dynamic LLM invocation target is required",
          null,
          request.modelSelector().modelId().orElse(null),
          false,
          Map.of(),
          null);
    }
    return request.invocationTarget();
  }

  private void ensureSupported(LlmInvocationTarget target, Set<LlmCapability> requiredCapabilities) {
    Set<LlmCapability> missing = EnumSet.noneOf(LlmCapability.class);
    missing.addAll(requiredCapabilities);
    missing.removeAll(target.supportedCapabilities());
    if (!missing.isEmpty()) {
      throw new LlmException(
          LlmErrorCode.UNSUPPORTED_CAPABILITY,
          "LLM invocation target does not support capabilities: " + missing,
          LlmProviderId.of(target.providerType().value()),
          target.upstreamModelId(),
          false,
          Map.of("missingCapabilities", missing.stream().map(Enum::name).toList()),
          null);
    }
  }

  private Set<LlmCapability> requiredCapabilities(LlmCompletionRequest request, boolean streaming) {
    EnumSet<LlmCapability> capabilities = EnumSet.copyOf(COMPLETION_CAPABILITIES);
    capabilities.addAll(request.modelSelector().requiredCapabilities());
    if (!request.tools().isEmpty()
        || request.toolChoice().mode() == LlmToolChoice.Mode.REQUIRED
        || request.toolChoice().mode() == LlmToolChoice.Mode.SPECIFIC) {
      capabilities.add(LlmCapability.TOOL_CALLING);
    }
    if (request.responseFormat() instanceof LlmResponseFormat.JsonObject) {
      capabilities.add(LlmCapability.STRUCTURED_OUTPUT);
    }
    if (request.responseFormat() instanceof LlmResponseFormat.JsonSchema) {
      capabilities.add(LlmCapability.JSON_SCHEMA_OUTPUT);
    }
    if (request.messages().stream().flatMap(message -> message.content().stream())
        .anyMatch(LlmContentPart.Image.class::isInstance)) {
      capabilities.add(LlmCapability.VISION_INPUT);
    }
    if (request.messages().stream().flatMap(message -> message.content().stream())
        .anyMatch(LlmContentPart.File.class::isInstance)) {
      capabilities.add(LlmCapability.FILE_INPUT);
    }
    if (streaming) {
      capabilities.add(LlmCapability.STREAMING);
    }
    if (request.options().reasoningEffort() != null) {
      capabilities.add(LlmCapability.REASONING_EFFORT);
    }
    return Set.copyOf(capabilities);
  }
}
