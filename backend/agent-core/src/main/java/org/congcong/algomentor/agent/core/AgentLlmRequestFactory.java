package org.congcong.algomentor.agent.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class AgentLlmRequestFactory {

  private final LlmModelSelector defaultModelSelector;
  private final AgentModelSelectorResolver modelSelectorResolver;

  public AgentLlmRequestFactory(LlmModelSelector defaultModelSelector) {
    this(defaultModelSelector, new DefaultAgentModelSelectorResolver());
  }

  public AgentLlmRequestFactory(
      LlmModelSelector defaultModelSelector,
      AgentModelSelectorResolver modelSelectorResolver
  ) {
    this.defaultModelSelector = validatedSelector(defaultModelSelector);
    this.modelSelectorResolver = Objects.requireNonNull(
        modelSelectorResolver,
        "agent model selector resolver must not be null");
  }

  static LlmCompletionRequest build(String model, AgentRequest request) {
    return build(selectorFromModel(model), request);
  }

  static LlmCompletionRequest build(LlmModelSelector modelSelector, AgentRequest request) {
    return new AgentLlmRequestFactory(modelSelector).build(request);
  }

  public LlmCompletionRequest build(AgentRequest request) {
    return build(request, 1, initialMessages(request), List.of(), null, request.metadata());
  }

  public List<LlmMessage> initialMessages(AgentRequest request) {
    return request.messages();
  }

  static LlmCompletionRequest build(String model, List<LlmMessage> messages, List<LlmToolSpec> tools) {
    return build(selectorFromModel(model), messages, tools);
  }

  static LlmCompletionRequest build(LlmModelSelector modelSelector, List<LlmMessage> messages, List<LlmToolSpec> tools) {
    return build(modelSelector, messages, tools, defaultToolChoice(tools));
  }

  static LlmCompletionRequest build(
      LlmModelSelector modelSelector,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice
  ) {
    return build(modelSelector, messages, tools, toolChoice, Map.of());
  }

  static LlmCompletionRequest build(
      LlmModelSelector modelSelector,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice,
      Map<String, Object> metadata
  ) {
    return LlmCompletionRequest.builder()
        .modelSelector(validatedSelector(modelSelector))
        .messages(messages)
        .tools(tools)
        .toolChoice(tools == null || tools.isEmpty() ? LlmToolChoice.none() : toolChoice)
        .metadata(metadata)
        .build();
  }

  static LlmCompletionRequest build(
      LlmModelSelector modelSelector,
      AgentRequest request,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice,
      Map<String, Object> metadata
  ) {
    return new AgentLlmRequestFactory(modelSelector).build(request, 1, messages, tools, toolChoice, metadata);
  }

  public LlmCompletionRequest build(
      AgentRequest request,
      int stepIndex,
      List<LlmMessage> messages,
      List<LlmToolSpec> tools,
      LlmToolChoice toolChoice,
      Map<String, Object> metadata
  ) {
    AgentExecutionOptions executionOptions = request.executionOptions();
    LlmModelSelector modelSelector = resolveModelSelector(new AgentModelSelectionContext(
        request,
        stepIndex,
        messages,
        tools,
        toolChoice,
        metadata,
        defaultModelSelector));
    return LlmCompletionRequest.builder()
        .modelSelector(modelSelector)
        .messages(messages)
        .options(executionOptions.generationOptions())
        .tools(tools)
        .toolChoice(tools == null || tools.isEmpty() ? LlmToolChoice.none() : toolChoice)
        .responseFormat(executionOptions.responseFormat())
        .metadata(executionMetadata(metadata, executionOptions.structuredOutput()))
        .build();
  }

  private LlmModelSelector resolveModelSelector(AgentModelSelectionContext context) {
    return validatedSelector(modelSelectorResolver.resolve(context));
  }

  private static Map<String, Object> executionMetadata(
      Map<String, Object> metadata,
      AgentStructuredOutputOptions structuredOutput
  ) {
    Map<String, Object> values = new LinkedHashMap<>();
    if (metadata != null) {
      values.putAll(metadata);
    }
    values.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_STRATEGY, structuredOutput.strategy().name());
    if (structuredOutput.schemaName() != null && !structuredOutput.schemaName().isBlank()) {
      values.put(AgentRuntimeMetadataKeys.SCHEMA_NAME, structuredOutput.schemaName());
    }
    if (structuredOutput.schemaVersion() != null && !structuredOutput.schemaVersion().isBlank()) {
      values.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, structuredOutput.schemaVersion());
    }
    return Map.copyOf(values);
  }

  static LlmModelSelector selectorFromModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Agent LLM model must not be blank");
    }
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), null);
  }

  private static LlmModelSelector validatedSelector(LlmModelSelector selector) {
    if (selector == null) {
      throw new IllegalArgumentException("Agent LLM model selector must not be null");
    }
    return selector;
  }

  private static LlmToolChoice defaultToolChoice(List<LlmToolSpec> tools) {
    return tools == null || tools.isEmpty() ? LlmToolChoice.none() : LlmToolChoice.auto();
  }
}
