package org.congcong.algomentor.ops.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class StructuredOpsLoggerTest {

  private final StructuredOpsLogger opsLogger = new StructuredOpsLogger();

  @Test
  void formatsStableKeyValuesAndRedactsSensitiveFields() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(OpsLogFields.PATH_TEMPLATE, "/api/practice/{sessionId}/messages");
    fields.put(OpsLogFields.AUTHORIZATION, "Bearer real-token");
    fields.put(OpsLogFields.REQUEST_ID, "request-1");
    fields.put(OpsLogFields.STATUS, 500);
    fields.put(OpsLogFields.METHOD, "POST");
    fields.put("zeta", "last");
    fields.put("alpha", "first");

    assertThat(opsLogger.format(OpsLogEventType.HTTP_REQUEST_FAILED, fields))
        .isEqualTo("eventType=http_request_failed requestId=request-1 method=POST "
            + "pathTemplate=/api/practice/{sessionId}/messages status=500 "
            + "alpha=first authorization=[REDACTED] zeta=last");
  }

  @Test
  void redactsSensitiveKeysCaseInsensitively() {
    String message = opsLogger.format(
        OpsLogEventType.AGENT_RUN_FAILED,
        Map.of(
            "X-ApiKey", "secret-1",
            "api_key", "secret-2",
            "jwtClaim", "secret-3",
            "Password", "secret-4",
            "clientCredential", "secret-5",
            "X-API-Key", "secret-6",
            "api-key", "secret-7",
            "api.key", "secret-8",
            "aiOutput", "secret-9",
            "ai_output", "secret-10"));

    assertThat(message)
        .contains("Password=[REDACTED]")
        .contains("X-API-Key=[REDACTED]")
        .contains("X-ApiKey=[REDACTED]")
        .contains("aiOutput=[REDACTED]")
        .contains("ai_output=[REDACTED]")
        .contains("api-key=[REDACTED]")
        .contains("api.key=[REDACTED]")
        .contains("api_key=[REDACTED]")
        .contains("clientCredential=[REDACTED]")
        .contains("jwtClaim=[REDACTED]")
        .doesNotContain("secret-1")
        .doesNotContain("secret-2")
        .doesNotContain("secret-3")
        .doesNotContain("secret-4")
        .doesNotContain("secret-5")
        .doesNotContain("secret-6")
        .doesNotContain("secret-7")
        .doesNotContain("secret-8")
        .doesNotContain("secret-9")
        .doesNotContain("secret-10");
  }

  @Test
  void ignoresSuppliedEventTypeField() {
    String message = opsLogger.format(
        OpsLogEventType.SSE_CONNECTION_FAILED,
        Map.of(
            OpsLogFields.EVENT_TYPE, "http_request_failed",
            OpsLogFields.REQUEST_ID, "request-1"));

    assertThat(message)
        .isEqualTo("eventType=sse_connection_failed requestId=request-1");
  }

  @Test
  void truncatesLongStringValuesToMaxLength() {
    String longValue = "x".repeat(300);

    String message = opsLogger.format(
        OpsLogEventType.PRACTICE_MESSAGE_STREAM_FAILED,
        Map.of("detail", longValue));

    String formattedValue = message.substring(message.indexOf("detail=") + "detail=".length());
    assertThat(formattedValue).hasSize(256);
    assertThat(formattedValue).doesNotContain(" ");
  }

  @Test
  void handlesNullFieldsAndNullValues() {
    assertThat(opsLogger.format(OpsLogEventType.SSE_CONNECTION_OPENED, null))
        .isEqualTo("eventType=sse_connection_opened");

    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(OpsLogFields.REQUEST_ID, null);

    assertThat(opsLogger.format(OpsLogEventType.SSE_CONNECTION_COMPLETED, fields))
        .isEqualTo("eventType=sse_connection_completed requestId=null");
  }

  @Test
  void sanitizesWhitespaceNewlinesAndUnsafeKeyCharacters() {
    String message = opsLogger.format(
        OpsLogEventType.SSE_CONNECTION_FAILED,
        Map.of("unsafe key", "first line\nsecond\tline with space"));

    assertThat(message)
        .isEqualTo("eventType=sse_connection_failed unsafe_key=first_line_second_line_with_space");
  }

  @Test
  void writesInfoAndWarnMessagesToProvidedLogger() {
    Logger logger = mock(Logger.class);
    IllegalStateException error = new IllegalStateException("failed");

    opsLogger.info(logger, OpsLogEventType.SSE_CONNECTION_TIMEOUT, Map.of(
        OpsLogFields.SSE_STREAM_TYPE, "practice_message"));
    opsLogger.warn(logger, OpsLogEventType.AGENT_TOOL_PERMISSION_TIMEOUT, Map.of(
        OpsLogFields.TOOL_NAME, "submit_practice_code_review"), error);

    verify(logger).info("eventType=sse_connection_timeout sseStreamType=practice_message");
    verify(logger).warn(
        "eventType=agent_tool_permission_timeout toolName=submit_practice_code_review",
        error);
  }

  @Test
  void writesWarnWithoutThrowableWhenThrowableIsNull() {
    Logger logger = mock(Logger.class);

    opsLogger.warn(logger, OpsLogEventType.AGENT_RUN_FAILED, Map.of(
        OpsLogFields.AGENT_SOURCE, "agent_conversation"), null);

    verify(logger).warn("eventType=agent_run_failed agentSource=agent_conversation");
  }

}
