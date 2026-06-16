package org.congcong.algomentor.llm.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.ResponseFormatJsonObject;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig.Schema;
import com.openai.models.responses.ResponseFunctionToolCall;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.ResponseUsage;
import com.openai.models.responses.Tool;
import com.openai.models.responses.ToolChoiceFunction;
import com.openai.models.responses.ToolChoiceOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

final class OpenAiResponsesMapper {

  private final ObjectMapper objectMapper;
  private final LlmProviderId providerId;

  OpenAiResponsesMapper(ObjectMapper objectMapper, LlmProviderId providerId) {
    this.objectMapper = objectMapper;
    this.providerId = providerId;
  }

  ResponseCreateParams toParams(LlmCompletionRequest request, LlmModelId modelId) {
    ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
        .model(modelId.value())
        .inputOfResponse(toInput(request.messages()));

    if (request.options().temperature() != null) {
      builder.temperature(request.options().temperature());
    }
    if (request.options().topP() != null) {
      builder.topP(request.options().topP());
    }
    if (request.options().maxOutputTokens() != null) {
      builder.maxOutputTokens(request.options().maxOutputTokens().longValue());
    }
    if (!request.tools().isEmpty()) {
      builder.tools(toTools(request.tools()));
      applyToolChoice(builder, request.toolChoice());
    }
    applyResponseFormat(builder, request.responseFormat());
    return builder.build();
  }

  LlmCompletionResult toResult(Response response) {
    LlmModelId modelId = LlmModelId.of(response.model().asString());
    String text = extractText(response);
    List<LlmToolCall> toolCalls = extractToolCalls(response);
    JsonNode structuredOutput = parseStructuredOutput(text);
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("responseId", response.id());
    response.status().ifPresent(status -> metadata.put("status", status.asString()));
    return new LlmCompletionResult(
        text.isEmpty() ? LlmMessage.assistant() : LlmMessage.assistant(text),
        toolCalls,
        structuredOutput,
        finishReason(response, toolCalls),
        toUsage(response.usage().orElse(null)),
        providerId,
        modelId,
        metadata);
  }

  LlmUsage toUsage(ResponseUsage usage) {
    if (usage == null) {
      return LlmUsage.empty();
    }
    return new LlmUsage(
        toInt(usage.inputTokens()),
        toInt(usage.outputTokens()),
        toInt(usage.inputTokensDetails().cachedTokens()),
        toInt(usage.outputTokensDetails().reasoningTokens()),
        toInt(usage.totalTokens()));
  }

  LlmToolCall toToolCall(ResponseFunctionToolCall call) {
    return new LlmToolCall(call.callId(), call.name(), parseArguments(call.arguments()));
  }

  LlmFinishReason finishReason(Response response) {
    return finishReason(response, extractToolCalls(response));
  }

  LlmFinishReason finishReason(Response response, List<LlmToolCall> toolCalls) {
    if (toolCalls != null && !toolCalls.isEmpty()) {
      return LlmFinishReason.TOOL_CALLS;
    }
    return response.status()
        .map(this::finishReason)
        .orElse(LlmFinishReason.UNKNOWN);
  }

  LlmFinishReason finishReason(ResponseStatus status) {
    if (ResponseStatus.COMPLETED.equals(status)) {
      return LlmFinishReason.STOP;
    }
    if (ResponseStatus.INCOMPLETE.equals(status)) {
      return LlmFinishReason.LENGTH;
    }
    if (ResponseStatus.FAILED.equals(status)) {
      return LlmFinishReason.ERROR;
    }
    if (ResponseStatus.CANCELLED.equals(status)) {
      return LlmFinishReason.ERROR;
    }
    return LlmFinishReason.UNKNOWN;
  }

  private List<ResponseInputItem> toInput(List<LlmMessage> messages) {
    List<ResponseInputItem> items = new ArrayList<>();
    for (LlmMessage message : messages) {
      if (message.role() == LlmMessage.Role.TOOL) {
        items.add(ResponseInputItem.ofFunctionCallOutput(ResponseInputItem.FunctionCallOutput.builder()
            .callId(message.toolCallId())
            .output(toText(message))
            .build()));
      } else if (message.role() == LlmMessage.Role.ASSISTANT && !message.toolCalls().isEmpty()) {
        message.toolCalls().stream()
            .map(this::toFunctionCallInput)
            .map(ResponseInputItem::ofFunctionCall)
            .forEach(items::add);
      } else {
        items.add(ResponseInputItem.ofEasyInputMessage(EasyInputMessage.builder()
            .role(toOpenAiRole(message.role()))
            .content(toText(message))
            .build()));
      }
    }
    return items;
  }

  private ResponseFunctionToolCall toFunctionCallInput(LlmToolCall toolCall) {
    return ResponseFunctionToolCall.builder()
        .callId(toolCall.id())
        .name(toolCall.name())
        .arguments(toolCall.arguments().toString())
        .build();
  }

  private String toText(LlmMessage message) {
    StringBuilder text = new StringBuilder();
    for (LlmContentPart part : message.content()) {
      if (part instanceof LlmContentPart.Text textPart) {
        text.append(textPart.text());
      } else if (part instanceof LlmContentPart.ToolResult toolResult) {
        text.append(toolResult.result().toString());
      } else if (part instanceof LlmContentPart.Image || part instanceof LlmContentPart.File) {
        throw new LlmException(
            LlmErrorCode.UNSUPPORTED_CAPABILITY,
            "OpenAI Responses mapper does not support image or file content in this version",
            providerId,
            null,
            false,
            Map.of(),
            null);
      } else {
        throw new LlmException(
            LlmErrorCode.UNSUPPORTED_CAPABILITY,
            "OpenAI Responses mapper does not support custom content in this version",
            providerId,
            null,
            false,
            Map.of(),
            null);
      }
    }
    return text.toString();
  }

  private EasyInputMessage.Role toOpenAiRole(LlmMessage.Role role) {
    return switch (role) {
      case SYSTEM -> EasyInputMessage.Role.SYSTEM;
      case USER -> EasyInputMessage.Role.USER;
      case ASSISTANT -> EasyInputMessage.Role.ASSISTANT;
      case TOOL -> throw new IllegalArgumentException("Tool messages are mapped as function call output items");
    };
  }

  private List<Tool> toTools(List<LlmToolSpec> tools) {
    return tools.stream()
        .map(tool -> Tool.ofFunction(FunctionTool.builder()
            .name(tool.name())
            .description(tool.description())
            .parameters(FunctionTool.Parameters.builder()
                .additionalProperties(toJsonValueMap(tool.inputSchema()))
                .build())
            .strict(tool.strict())
            .build()))
        .toList();
  }

  private void applyToolChoice(ResponseCreateParams.Builder builder, LlmToolChoice toolChoice) {
    switch (toolChoice.mode()) {
      case AUTO -> builder.toolChoice(ToolChoiceOptions.AUTO);
      case NONE -> builder.toolChoice(ToolChoiceOptions.NONE);
      case REQUIRED -> builder.toolChoice(ToolChoiceOptions.REQUIRED);
      case SPECIFIC -> builder.toolChoice(ToolChoiceFunction.builder().name(toolChoice.toolName()).build());
    }
  }

  private void applyResponseFormat(ResponseCreateParams.Builder builder, LlmResponseFormat responseFormat) {
    if (responseFormat instanceof LlmResponseFormat.JsonObject) {
      builder.text(ResponseTextConfig.builder()
          .format(ResponseFormatJsonObject.builder().build())
          .build());
      return;
    }
    if (responseFormat instanceof LlmResponseFormat.JsonSchema jsonSchema) {
      builder.text(ResponseTextConfig.builder()
          .format(ResponseFormatTextJsonSchemaConfig.builder()
              .name(jsonSchema.name())
              .schema(Schema.builder()
                  .additionalProperties(toJsonValueMap(jsonSchema.schema()))
                  .build())
              .strict(jsonSchema.strict())
              .build())
          .build());
    }
  }

  private String extractText(Response response) {
    StringBuilder text = new StringBuilder();
    for (var item : response.output()) {
      item.message().ifPresent(message -> appendMessageText(text, message));
    }
    return text.toString();
  }

  private void appendMessageText(StringBuilder target, ResponseOutputMessage message) {
    for (ResponseOutputMessage.Content content : message.content()) {
      content.outputText().map(ResponseOutputText::text).ifPresent(target::append);
    }
  }

  private List<LlmToolCall> extractToolCalls(Response response) {
    List<LlmToolCall> calls = new ArrayList<>();
    for (var item : response.output()) {
      item.functionCall().map(this::toToolCall).ifPresent(calls::add);
    }
    return List.copyOf(calls);
  }

  private JsonNode parseStructuredOutput(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(text);
    } catch (JsonProcessingException ignored) {
      return null;
    }
  }

  private JsonNode parseArguments(String arguments) {
    try {
      return objectMapper.readTree(arguments);
    } catch (JsonProcessingException ex) {
      throw new LlmException(
          LlmErrorCode.RESPONSE_PARSE_FAILED,
          "Failed to parse OpenAI tool call arguments",
          providerId,
          null,
          false,
          Map.of(),
          ex);
    }
  }

  private Map<String, JsonValue> toJsonValueMap(JsonNode node) {
    if (node == null || !node.isObject()) {
      return Map.of();
    }
    Map<String, JsonValue> values = new LinkedHashMap<>();
    node.fields().forEachRemaining(entry -> values.put(entry.getKey(), JsonValue.fromJsonNode(entry.getValue())));
    return values;
  }

  private int toInt(long value) {
    return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
  }
}
