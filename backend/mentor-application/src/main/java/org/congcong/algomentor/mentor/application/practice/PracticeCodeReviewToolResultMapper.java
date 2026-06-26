package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class PracticeCodeReviewToolResultMapper {

  private static final String METADATA_REVIEW_ATTEMPT_FAILURE_CODE = "reviewAttemptFailureCode";

  private final ObjectMapper objectMapper;

  public PracticeCodeReviewToolResultMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  public ObjectNode map(PracticeReviewResult result, PracticeTurnContext context) {
    Objects.requireNonNull(result, "result must not be null");
    Objects.requireNonNull(context, "context must not be null");

    Map<String, Object> metadata = result.metadata();
    ObjectNode node = objectMapper.createObjectNode();
    node.put(
        PracticeCodeReviewAgentToolNames.RESULT_TYPE,
        PracticeCodeReviewAgentToolNames.RESULT_TYPE_PRACTICE_CODE_REVIEW_SUBMITTED);
    node.put(PracticeCodeReviewAgentToolNames.RESULT_STATUS, result.status().name());
    putLongOrNull(
        node,
        PracticeCodeReviewAgentToolNames.RESULT_REVIEW_ID,
        metadata.get(PracticeCodeReviewAgentToolNames.RESULT_REVIEW_ID));
    putIntegerOrNull(
        node,
        PracticeCodeReviewAgentToolNames.RESULT_VERSION_NO,
        metadata.get(PracticeCodeReviewAgentToolNames.RESULT_VERSION_NO));
    putDecimalOrNull(
        node,
        PracticeCodeReviewAgentToolNames.RESULT_TOTAL_SCORE,
        metadata.get(PracticeCodeReviewAgentToolNames.RESULT_TOTAL_SCORE));
    putBooleanOrNull(
        node,
        PracticeCodeReviewAgentToolNames.RESULT_PASSED,
        metadata.get(PracticeCodeReviewAgentToolNames.RESULT_PASSED));
    putStringOrNull(node, PracticeCodeReviewAgentToolNames.RESULT_FAILURE_CODE, failureCode(result, metadata));
    node.put(PracticeCodeReviewAgentToolNames.RESULT_PROBLEM_SLUG, context.problemSlug());
    node.put(PracticeCodeReviewAgentToolNames.RESULT_SESSION_ID, context.sessionId());
    node.put(PracticeCodeReviewAgentToolNames.RESULT_USER_MESSAGE_ID, context.userMessageId());
    putLongOrNull(node, PracticeCodeReviewAgentToolNames.RESULT_AGENT_RUN_DB_ID, context.agentRunDbId());
    node.put(PracticeCodeReviewAgentToolNames.RESULT_MESSAGE, message(result));
    return node;
  }

  private String message(PracticeReviewResult result) {
    return switch (result.status()) {
      case SAVED -> "代码 Review 已提交。";
      case NOT_CODE_LIKE -> "本轮内容不像代码提交，未形成有效 Review。";
      case NOT_COMPLETE_SUBMISSION -> "本轮内容不是完整代码提交，未形成有效 Review。";
      case FAILED -> "代码 Review 未能完成。";
      case REVIEWED -> "代码 Review 已完成，等待保存。";
    };
  }

  private String failureCode(PracticeReviewResult result, Map<String, Object> metadata) {
    if (result.failureCode() != null && !result.failureCode().isBlank()) {
      return result.failureCode();
    }
    Object value = metadata.get(METADATA_REVIEW_ATTEMPT_FAILURE_CODE);
    return value instanceof CharSequence text && !text.toString().isBlank() ? text.toString().trim() : null;
  }

  private void putLongOrNull(ObjectNode node, String fieldName, Object value) {
    Long parsed = asLong(value);
    if (parsed == null) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, parsed);
  }

  private void putIntegerOrNull(ObjectNode node, String fieldName, Object value) {
    Long parsed = asLong(value);
    if (parsed == null) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, parsed.intValue());
  }

  private void putDecimalOrNull(ObjectNode node, String fieldName, Object value) {
    BigDecimal parsed = asBigDecimal(value);
    if (parsed == null) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, parsed);
  }

  private void putBooleanOrNull(ObjectNode node, String fieldName, Object value) {
    Boolean parsed = asBoolean(value);
    if (parsed == null) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, parsed);
  }

  private void putStringOrNull(ObjectNode node, String fieldName, String value) {
    if (value == null || value.isBlank()) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, value);
  }

  private Long asLong(Object value) {
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

  private BigDecimal asBigDecimal(Object value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    if (value instanceof CharSequence text) {
      try {
        return new BigDecimal(text.toString().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Boolean asBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof CharSequence text) {
      String normalized = text.toString().trim();
      if ("true".equalsIgnoreCase(normalized)) {
        return true;
      }
      if ("false".equalsIgnoreCase(normalized)) {
        return false;
      }
    }
    return null;
  }
}
