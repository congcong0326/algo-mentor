package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

public final class PracticeCodeReviewAgentTool implements AgentTool {

  private static final String JSON_TYPE = "type";
  private static final String JSON_OBJECT = "object";
  private static final String JSON_STRING = "string";
  private static final String JSON_PROPERTIES = "properties";
  private static final String JSON_REQUIRED = "required";
  private static final String JSON_ADDITIONAL_PROPERTIES = "additionalProperties";
  private static final String JSON_DESCRIPTION = "description";

  private static final String ERROR_MISSING_METADATA = "MISSING_METADATA";
  private static final String ERROR_NOT_PRACTICE_CHAT = "NOT_PRACTICE_CHAT";
  private static final String ERROR_PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
  private static final String ERROR_TURN_MESSAGE_LOOKUP_MISSING = "TURN_MESSAGE_LOOKUP_MISSING";
  private static final String ERROR_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  private static final String ERROR_REVIEW_SERVICE_FAILED = "REVIEW_SERVICE_FAILED";
  private static final String ERROR_RESULT_MAPPING_FAILED = "RESULT_MAPPING_FAILED";

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
      """
          Submit the current practice chat user message for a formal code review. Use only when the user appears to \
          request or provide a code submission for the active practice problem. Do not pass user id, session id, \
          problem slug, code, or message ids; the server derives them from trusted execution metadata.
          """.strip(),
      inputSchema(),
      true);

  private final PracticeSessionRepository sessionRepository;
  private final AgentTurnMessageLookupRepository turnMessageLookupRepository;
  private final PracticeCodeReviewService reviewService;
  private final PracticeCodeReviewToolResultMapper resultMapper;

  public PracticeCodeReviewAgentTool(
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService,
      ObjectMapper objectMapper
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.turnMessageLookupRepository = Objects.requireNonNull(
        turnMessageLookupRepository,
        "turnMessageLookupRepository must not be null");
    this.reviewService = Objects.requireNonNull(reviewService, "reviewService must not be null");
    this.resultMapper = new PracticeCodeReviewToolResultMapper(
        Objects.requireNonNull(objectMapper, "objectMapper must not be null"));
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    validateArguments(arguments);
    if (context == null) {
      throw failure("Practice code review tool requires execution context", ERROR_MISSING_METADATA, Map.of(), null);
    }
    Map<String, Object> metadata = context.requestMetadata();
    validatePracticeChat(metadata);

    long userId = requiredPositiveLong(metadata, AgentRuntimeMetadataKeys.USER_ID);
    long sessionId = requiredPositiveLong(metadata, PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID);
    long runDbId = requiredPositiveLong(metadata, AgentRuntimeMetadataKeys.RUN_DB_ID);
    Map<String, Object> errorIds = errorIds(metadata, userId, sessionId, runDbId);

    PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> failure(
            "Practice session was not found for current user",
            ERROR_PRACTICE_SESSION_NOT_FOUND,
            errorIds,
            null));
    AgentTurnMessages turnMessages = turnMessageLookupRepository.findByRunId(runDbId)
        .orElseThrow(() -> failure(
            "Practice run messages were not found",
            ERROR_TURN_MESSAGE_LOOKUP_MISSING,
            errorIds,
            null));
    AgentMessage userMessage = turnMessages.userMessage();

    PracticeTurnContext turnContext = new PracticeTurnContext(
        userId,
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        session.id(),
        userMessage.id(),
        turnMessages.assistantMessage().map(AgentMessage::id).orElse(null),
        runDbId,
        "",
        "",
        userMessage.content(),
        userMessage.content(),
        "",
        session.locale());

    PracticeReviewResult reviewResult;
    try {
      reviewResult = reviewService.review(turnContext);
    } catch (AgentException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw failure("Practice code review service failed", ERROR_REVIEW_SERVICE_FAILED, errorIds, exception);
    }

    try {
      return resultMapper.map(reviewResult, turnContext);
    } catch (RuntimeException exception) {
      throw failure("Practice code review result mapping failed", ERROR_RESULT_MAPPING_FAILED, errorIds, exception);
    }
  }

  private void validatePracticeChat(Map<String, Object> metadata) {
    Object scenario = metadata.get(PracticeChatPromptConstants.METADATA_SCENARIO);
    if (scenario == null || scenario.toString().isBlank()) {
      throw failure("Practice chat metadata is missing", ERROR_MISSING_METADATA, Map.of(), null);
    }
    if (!PracticeChatPromptConstants.SCENARIO.equals(scenario.toString())) {
      throw failure("Practice code review tool can only run in practice chat", ERROR_NOT_PRACTICE_CHAT, Map.of(), null);
    }
  }

  private void validateArguments(JsonNode arguments) {
    if (arguments == null || arguments.isNull()) {
      return;
    }
    if (!arguments.isObject()) {
      throw failure("Practice code review tool arguments must be a JSON object", ERROR_INVALID_ARGUMENTS, Map.of(), null);
    }
    java.util.Iterator<String> fields = arguments.fieldNames();
    while (fields.hasNext()) {
      String fieldName = fields.next();
      if (!PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT.equals(fieldName)
          && !PracticeCodeReviewAgentToolNames.ARGUMENT_NOTES.equals(fieldName)) {
        throw failure("Practice code review tool arguments contain unsupported fields", ERROR_INVALID_ARGUMENTS, Map.of(), null);
      }
      JsonNode value = arguments.get(fieldName);
      if (value != null && !value.isNull() && !value.isTextual()) {
        throw failure("Practice code review tool arguments must be strings", ERROR_INVALID_ARGUMENTS, Map.of(), null);
      }
    }
  }

  private long requiredPositiveLong(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    Long parsed = parseLong(value);
    if (parsed == null || parsed < 1) {
      throw failure("Practice code review tool metadata is missing", ERROR_MISSING_METADATA, errorIds(metadata), null);
    }
    return parsed;
  }

  private Long parseLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof CharSequence text) {
      try {
        return Long.parseLong(text.toString().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Map<String, Object> errorIds(Map<String, Object> metadata) {
    return errorIds(
        metadata,
        positiveLongOrNull(metadata.get(AgentRuntimeMetadataKeys.USER_ID)),
        positiveLongOrNull(metadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID)),
        positiveLongOrNull(metadata.get(AgentRuntimeMetadataKeys.RUN_DB_ID)));
  }

  private Map<String, Object> errorIds(Map<String, Object> metadata, long userId, long sessionId, long runDbId) {
    return errorIds(metadata, Long.valueOf(userId), Long.valueOf(sessionId), Long.valueOf(runDbId));
  }

  private Map<String, Object> errorIds(Map<String, Object> metadata, Long userId, Long sessionId, Long runDbId) {
    LinkedHashMap<String, Object> ids = new LinkedHashMap<>();
    putPositive(ids, AgentRuntimeMetadataKeys.USER_ID, userId);
    putPositive(ids, PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId);
    putPositive(ids, AgentRuntimeMetadataKeys.RUN_DB_ID, runDbId);
    putPositive(
        ids,
        AgentRuntimeMetadataKeys.TASK_ID,
        positiveLongOrNull(metadata.get(AgentRuntimeMetadataKeys.TASK_ID)));
    putPositive(
        ids,
        AgentRuntimeMetadataKeys.TURN_ID,
        positiveLongOrNull(metadata.get(AgentRuntimeMetadataKeys.TURN_ID)));
    return ids;
  }

  private Long positiveLongOrNull(Object value) {
    Long parsed = parseLong(value);
    return parsed == null || parsed < 1 ? null : parsed;
  }

  private void putPositive(Map<String, Object> ids, String key, Long value) {
    if (value != null && value > 0) {
      ids.put(key, value);
    }
  }

  private AgentException failure(String message, String errorType, Map<String, Object> ids, Throwable cause) {
    LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AgentRuntimeMetadataKeys.TOOL_NAME, PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW);
    metadata.put(AgentRuntimeMetadataKeys.ERROR_TYPE, errorType);
    if (ids != null) {
      metadata.putAll(ids);
    }
    return new AgentException(
        AgentErrorCode.TOOL_EXECUTION_FAILED,
        message,
        false,
        metadata,
        cause);
  }

  private static JsonNode inputSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put(JSON_TYPE, JSON_OBJECT);
    ObjectNode properties = schema.putObject(JSON_PROPERTIES);
    properties.set(
        PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT,
        stringProperty("Optional user-facing reason for requesting this formal review."));
    properties.set(
        PracticeCodeReviewAgentToolNames.ARGUMENT_NOTES,
        stringProperty("Optional short notes for the review request. Keep this concise and non-identifying."));
    schema.putArray(JSON_REQUIRED);
    schema.put(JSON_ADDITIONAL_PROPERTIES, false);
    return schema;
  }

  private static ObjectNode stringProperty(String description) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put(JSON_TYPE, JSON_STRING);
    node.put(JSON_DESCRIPTION, description);
    return node;
  }
}
