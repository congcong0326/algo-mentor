package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContract;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;

/** 仅从受信 Agent 上下文读取身份的长期用户自述画像工具。 */
public final class UpdateLearnerDeclaredProfileAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerDeclaredProfileToolContracts.TOOL_NAME,
      """
          Update the learner's long-term declared profile only when the current user explicitly states or corrects a
          stable background, goal, time constraint, learning preference, or self-assessed ability. Use one batch for
          all relevant dimensions. Do not use for one-off performance, transient emotions, guesses, or short-lived
          questions. The server derives user identity and profile scope; pass only updates with dimension, statement,
          and intent.
          """.strip(),
      inputSchema(),
      true);

  private final DeclaredProfileUpdateService updateService;
  private final ObjectMapper objectMapper;

  public UpdateLearnerDeclaredProfileAgentTool(
      DeclaredProfileUpdateService updateService,
      ObjectMapper objectMapper
  ) {
    this.updateService = Objects.requireNonNull(updateService, "updateService must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    DeclaredProfileUpdateRequest request;
    try {
      request = DeclaredProfileUpdateRequest.fromJson(arguments);
      if (context == null || !PracticeChatPromptConstants.SCENARIO.equals(
          String.valueOf(context.requestMetadata().get(PracticeChatPromptConstants.METADATA_SCENARIO)))) {
        return failed(request);
      }
      Long userId = positiveLong(context.requestMetadata(), AgentRuntimeMetadataKeys.USER_ID);
      Long parentRunDbId = positiveLong(context.requestMetadata(), AgentRuntimeMetadataKeys.RUN_DB_ID);
      if (userId == null || parentRunDbId == null) {
        return failed(request);
      }
      return updateService.update(
          userId,
          request,
          parentRunDbId,
          context.stepIndex())
          .toJson(objectMapper);
    } catch (RuntimeException exception) {
      return DeclaredProfileUpdateResult.failed(List.of()).toJson(objectMapper);
    }
  }

  private JsonNode failed(DeclaredProfileUpdateRequest request) {
    return DeclaredProfileUpdateResult.failed(request.updates().stream()
        .map(DeclaredProfileUpdateRequest.Item::dimension).toList()).toJson(objectMapper);
  }

  private Long positiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    try {
      long parsed = value instanceof Number number
          ? number.longValue()
          : Long.parseLong(String.valueOf(value).trim());
      return parsed > 0 ? parsed : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private static JsonNode inputSchema() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    ObjectNode updates = properties.putObject(LearnerDeclaredProfileToolContracts.ARGUMENT_UPDATES);
    updates.put("type", "array");
    updates.put("minItems", 1);
    updates.set("items", updateItemSchema());
    root.putArray("required").add(LearnerDeclaredProfileToolContracts.ARGUMENT_UPDATES);
    return root;
  }

  private static JsonNode updateItemSchema() {
    ObjectNode item = JsonNodeFactory.instance.objectNode();
    item.put("type", "object");
    item.put("additionalProperties", false);
    ObjectNode properties = item.putObject("properties");
    properties.set(LearnerDeclaredProfileToolContracts.UPDATE_DIMENSION, enumSchema(
        LearnerProfileContract.declaredDimensions().stream().map(Enum::name).sorted().toList()));
    properties.set(LearnerDeclaredProfileToolContracts.UPDATE_STATEMENT, stringSchema());
    properties.set(LearnerDeclaredProfileToolContracts.UPDATE_INTENT, enumSchema(
        java.util.Arrays.stream(DeclaredProfileUpdateIntent.values()).map(Enum::name).toList()));
    ArrayNode required = item.putArray("required");
    required.add(LearnerDeclaredProfileToolContracts.UPDATE_DIMENSION);
    required.add(LearnerDeclaredProfileToolContracts.UPDATE_STATEMENT);
    required.add(LearnerDeclaredProfileToolContracts.UPDATE_INTENT);
    return item;
  }

  private static JsonNode stringSchema() {
    ObjectNode string = JsonNodeFactory.instance.objectNode();
    string.put("type", "string");
    return string;
  }

  private static JsonNode enumSchema(List<String> values) {
    ObjectNode schema = (ObjectNode) stringSchema();
    ArrayNode enumeration = schema.putArray("enum");
    values.forEach(enumeration::add);
    return schema;
  }
}
