package org.congcong.algomentor.agent.core;

import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;

/**
 * 单次 Agent run 的模型执行配置。
 */
public record AgentExecutionOptions(
    LlmModelSelector modelSelector,
    LlmGenerationOptions generationOptions,
    LlmResponseFormat responseFormat,
    AgentStructuredOutputOptions structuredOutput
) {

  public AgentExecutionOptions(
      LlmGenerationOptions generationOptions,
      LlmResponseFormat responseFormat,
      AgentStructuredOutputOptions structuredOutput
  ) {
    this(null, generationOptions, responseFormat, structuredOutput);
  }

  public AgentExecutionOptions {
    generationOptions = generationOptions == null ? LlmGenerationOptions.defaults() : generationOptions;
    responseFormat = responseFormat == null ? new LlmResponseFormat.Text() : responseFormat;
    structuredOutput = structuredOutput == null ? AgentStructuredOutputOptions.none() : structuredOutput;
    validateStructuredOutput(responseFormat, structuredOutput);
  }

  public static AgentExecutionOptions defaults() {
    return new AgentExecutionOptions(
        null,
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.Text(),
        AgentStructuredOutputOptions.none());
  }

  private static void validateStructuredOutput(
      LlmResponseFormat responseFormat,
      AgentStructuredOutputOptions structuredOutput
  ) {
    if (structuredOutput.strategy() == StructuredOutputStrategy.PROVIDER_NATIVE
        && !(responseFormat instanceof LlmResponseFormat.JsonObject
            || responseFormat instanceof LlmResponseFormat.JsonSchema)) {
      throw new IllegalArgumentException("Provider-native structured output requires a JSON response format");
    }
    if (structuredOutput.strategy() == StructuredOutputStrategy.AUTO
        || structuredOutput.strategy() == StructuredOutputStrategy.TOOL_CALL) {
      throw new IllegalArgumentException("Structured output strategy is not enabled: " + structuredOutput.strategy());
    }
  }
}
