package org.congcong.algomentor.llm.core;

import java.util.List;
import java.util.Map;

public record LlmCompletionRequest(
    LlmModelSelector modelSelector,
    List<LlmMessage> messages,
    LlmGenerationOptions options,
    List<LlmToolSpec> tools,
    LlmToolChoice toolChoice,
    LlmResponseFormat responseFormat,
    Map<String, Object> metadata
) {

  public LlmCompletionRequest {
    if (modelSelector == null) {
      throw new IllegalArgumentException("LLM model selector must not be null");
    }
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("LLM completion request must include at least one message");
    }
    messages = List.copyOf(messages);
    options = options == null ? LlmGenerationOptions.defaults() : options;
    tools = tools == null ? List.of() : List.copyOf(tools);
    toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    responseFormat = responseFormat == null ? new LlmResponseFormat.Text() : responseFormat;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private LlmModelSelector modelSelector;
    private List<LlmMessage> messages;
    private LlmGenerationOptions options;
    private List<LlmToolSpec> tools;
    private LlmToolChoice toolChoice;
    private LlmResponseFormat responseFormat;
    private Map<String, Object> metadata;

    public Builder modelSelector(LlmModelSelector modelSelector) {
      this.modelSelector = modelSelector;
      return this;
    }

    public Builder messages(List<LlmMessage> messages) {
      this.messages = messages;
      return this;
    }

    public Builder options(LlmGenerationOptions options) {
      this.options = options;
      return this;
    }

    public Builder tools(List<LlmToolSpec> tools) {
      this.tools = tools;
      return this;
    }

    public Builder toolChoice(LlmToolChoice toolChoice) {
      this.toolChoice = toolChoice;
      return this;
    }

    public Builder responseFormat(LlmResponseFormat responseFormat) {
      this.responseFormat = responseFormat;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public LlmCompletionRequest build() {
      return new LlmCompletionRequest(modelSelector, messages, options, tools, toolChoice, responseFormat, metadata);
    }
  }
}
