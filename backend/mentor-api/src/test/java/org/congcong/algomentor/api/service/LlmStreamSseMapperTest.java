package org.congcong.algomentor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class LlmStreamSseMapperTest {

  private final LlmStreamSseMapper mapper = new LlmStreamSseMapper();
  private final ObjectMapper objectMapper = new ObjectMapper()
      .findAndRegisterModules()
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void mapsContentDeltaToNamedJsonEvent() throws Exception {
    SseEmitter.SseEventBuilder event = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("hello")));

    assertThat(sseText(event)).contains("event:content_delta");
    assertThat(serializedData(event)).contains("\"content\":\"hello\"");
  }

  @Test
  void mapsToolCallEventsToNamedJsonEvents() throws Exception {
    SseEmitter.SseEventBuilder start = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.ToolCallStart("call_1", "search")));
    SseEmitter.SseEventBuilder delta = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.ToolCallDelta("call_1", "{\"q\"")));
    LlmToolCall toolCall = new LlmToolCall("call_1", "search", objectMapper.readTree("{\"q\":\"binary\"}"));
    SseEmitter.SseEventBuilder end = mapper.toSseEvent(AgentStreamEvent.fromLlm(new LlmStreamEvent.ToolCallEnd(toolCall)));

    assertThat(sseText(start)).contains("event:tool_call_start");
    assertThat(serializedData(start)).contains("\"id\":\"call_1\"", "\"name\":\"search\"");
    assertThat(sseText(delta)).contains("event:tool_call_delta");
    assertThat(serializedData(delta)).contains("\"argumentsDelta\":\"{\\\"q\\\"\"");
    assertThat(sseText(end)).contains("event:tool_call_end");
    assertThat(serializedData(end)).contains("\"toolCall\"", "\"arguments\":{\"q\":\"binary\"}");
  }

  @Test
  void mapsTerminalAndAccountingEventsToNamedJsonEvents() throws Exception {
    SseEmitter.SseEventBuilder start = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.MessageStart(LlmProviderId.of("openai"), LlmModelId.of("gpt-test"))));
    SseEmitter.SseEventBuilder usage = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.Usage(new LlmUsage(2, 3, 1, 0, 5))));
    SseEmitter.SseEventBuilder end = mapper.toSseEvent(
        AgentStreamEvent.fromLlm(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of("responseId", "resp_1"))));
    SseEmitter.SseEventBuilder heartbeat = mapper.toSseEvent(AgentStreamEvent.fromLlm(new LlmStreamEvent.Heartbeat()));

    assertThat(sseText(start)).contains("event:message_start");
    assertThat(serializedData(start)).contains("\"provider\":\"openai\"", "\"model\":\"gpt-test\"");
    assertThat(sseText(usage)).contains("event:usage");
    assertThat(serializedData(usage)).contains("\"totalTokens\":5");
    assertThat(sseText(end)).contains("event:message_end");
    assertThat(serializedData(end)).contains("\"finishReason\":\"STOP\"", "\"responseId\":\"resp_1\"");
    assertThat(sseText(heartbeat)).contains("event:heartbeat");
    assertThat(serializedData(heartbeat)).isEqualTo("{}");
  }

  @Test
  void mapsErrorsWithoutProviderSecrets() throws Exception {
    LlmException exception = new LlmException(
        LlmErrorCode.PROVIDER_UNAVAILABLE,
        "provider is unavailable",
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-test"),
        true,
        Map.of("requestId", "req_1"),
        null);

    SseEmitter.SseEventBuilder event = mapper.toSseEvent(AgentStreamEvent.fromLlm(new LlmStreamEvent.Error(exception)));

    assertThat(sseText(event)).contains("event:error");
    assertThat(serializedData(event))
        .contains("\"code\":\"PROVIDER_UNAVAILABLE\"")
        .contains("\"message\":\"provider is unavailable\"")
        .contains("\"retryable\":true")
        .contains("\"provider\":\"openai\"")
        .contains("\"model\":\"gpt-test\"")
        .contains("\"requestId\":\"req_1\"");
  }

  @Test
  void mapsAgentLifecycleEventsToNamedJsonEvents() throws Exception {
    SseEmitter.SseEventBuilder runStart = mapper.toSseEvent(
        new AgentStreamEvent.AgentRunStart("run_1", "two pointers", 4, Map.of(
            AgentRuntimeMetadataKeys.TASK_ID, 11L,
            AgentRuntimeMetadataKeys.TURN_ID, 21L,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)));
    SseEmitter.SseEventBuilder stepStart = mapper.toSseEvent(
        new AgentStreamEvent.AgentStepStart("run_1", 1));
    SseEmitter.SseEventBuilder runEnd = mapper.toSseEvent(
        new AgentStreamEvent.AgentRunEnd("run_1", 1, LlmFinishReason.STOP, Map.of()));

    assertThat(sseText(runStart)).contains("event:agent_run_start");
    assertThat(serializedData(runStart)).contains(
        "\"runId\":\"run_1\"",
        "\"topic\":\"two pointers\"",
        "\"maxSteps\":4",
        "\"taskId\":11",
        "\"turnId\":21",
        "\"runDbId\":31");
    assertThat(sseText(stepStart)).contains("event:agent_step_start");
    assertThat(serializedData(stepStart)).contains("\"stepIndex\":1");
    assertThat(sseText(runEnd)).contains("event:agent_run_end");
    assertThat(serializedData(runEnd)).contains("\"finishReason\":\"STOP\"");
  }

  @Test
  void mapsToolPermissionRequestToLowSensitivityNamedJsonEvent() throws Exception {
    SseEmitter.SseEventBuilder event = mapper.toSseEvent(
        new AgentStreamEvent.ToolPermissionRequest(
            "perm_1",
            "run_1",
            2,
            "call_1",
            "review.list_notes",
            "读取复盘笔记",
            "需要读取最近的复盘笔记生成练习建议。",
            Map.of("scope", "recent_notes", "limit", 3),
            Instant.parse("2026-06-26T10:15:30Z")));

    assertThat(sseText(event)).contains("event:tool_permission_request");
    assertThat(serializedData(event))
        .contains("\"runId\":\"run_1\"")
        .contains("\"stepIndex\":2")
        .contains("\"toolCallId\":\"call_1\"")
        .contains("\"toolName\":\"review.list_notes\"")
        .contains("\"permissionRequestId\":\"perm_1\"")
        .contains("\"displayName\":\"读取复盘笔记\"")
        .contains("\"reason\":\"需要读取最近的复盘笔记生成练习建议。\"")
        .contains("\"preview\":", "\"scope\":\"recent_notes\"", "\"limit\":3")
        .contains("\"expiresAt\":\"2026-06-26T10:15:30Z\"")
        .doesNotContain("metadata", "trustedMetadata", "arguments", "userId", "ownerUserId");
  }

  @Test
  void mapsToolPermissionDecisionToLowSensitivityNamedJsonEvent() throws Exception {
    SseEmitter.SseEventBuilder event = mapper.toSseEvent(
        new AgentStreamEvent.ToolPermissionDecision(
            "perm_1",
            "run_1",
            2,
            "call_1",
            "review.list_notes",
            AgentToolPermissionDecisionType.ALLOW,
            "用户允许本次读取。",
            Instant.parse("2026-06-26T10:16:00Z")));

    assertThat(sseText(event)).contains("event:tool_permission_decision");
    assertThat(serializedData(event))
        .contains("\"runId\":\"run_1\"")
        .contains("\"stepIndex\":2")
        .contains("\"toolCallId\":\"call_1\"")
        .contains("\"toolName\":\"review.list_notes\"")
        .contains("\"permissionRequestId\":\"perm_1\"")
        .contains("\"decision\":\"ALLOW\"")
        .contains("\"reason\":\"用户允许本次读取。\"")
        .contains("\"decidedAt\":\"2026-06-26T10:16:00Z\"")
        .doesNotContain("metadata", "trustedMetadata", "arguments", "userId", "ownerUserId");
  }

  @Test
  void mapsToolPermissionTimeoutToLowSensitivityNamedJsonEvent() throws Exception {
    SseEmitter.SseEventBuilder event = mapper.toSseEvent(
        new AgentStreamEvent.ToolPermissionTimeout(
            "perm_1",
            "run_1",
            2,
            "call_1",
            "review.list_notes",
            "用户未在有效期内决策。",
            Instant.parse("2026-06-26T10:17:00Z")));

    assertThat(sseText(event)).contains("event:tool_permission_timeout");
    assertThat(serializedData(event))
        .contains("\"runId\":\"run_1\"")
        .contains("\"stepIndex\":2")
        .contains("\"toolCallId\":\"call_1\"")
        .contains("\"toolName\":\"review.list_notes\"")
        .contains("\"permissionRequestId\":\"perm_1\"")
        .contains("\"reason\":\"用户未在有效期内决策。\"")
        .contains("\"expiredAt\":\"2026-06-26T10:17:00Z\"")
        .doesNotContain("metadata", "trustedMetadata", "arguments", "userId", "ownerUserId");
  }

  @Test
  void mapsAgentErrorsWithoutSecrets() throws Exception {
    AgentException exception = new AgentException(
        AgentErrorCode.UNKNOWN_TOOL,
        "Unknown agent tool: search",
        false,
        Map.of("toolName", "search"),
        null);

    SseEmitter.SseEventBuilder event = mapper.toSseEvent(new AgentStreamEvent.AgentError("run_1", exception));

    assertThat(sseText(event)).contains("event:agent_error");
    assertThat(serializedData(event))
        .contains("\"code\":\"UNKNOWN_TOOL\"")
        .contains("\"message\":\"Unknown agent tool: search\"")
        .contains("\"retryable\":false")
        .contains("\"toolName\":\"search\"");
  }

  private String sseText(SseEmitter.SseEventBuilder event) {
    return event.build().stream()
        .map(ResponseBodyEmitter.DataWithMediaType::getData)
        .map(Object::toString)
        .reduce("", String::concat);
  }

  private String serializedData(SseEmitter.SseEventBuilder event) throws Exception {
    Object data = event.build().stream()
        .filter(item -> item.getMediaType() == null || MediaType.APPLICATION_JSON.includes(item.getMediaType()))
        .map(ResponseBodyEmitter.DataWithMediaType::getData)
        .filter(candidate -> !(candidate instanceof String text) || !text.startsWith("event:"))
        .filter(candidate -> !(candidate instanceof String text) || !text.isBlank())
        .findFirst()
        .orElseThrow();
    return data instanceof String text ? text : objectMapper.writeValueAsString(data);
  }
}
