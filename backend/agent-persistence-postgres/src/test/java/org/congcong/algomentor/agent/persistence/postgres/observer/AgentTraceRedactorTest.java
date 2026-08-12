package org.congcong.algomentor.agent.persistence.postgres.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobInsertRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.agent.persistence.postgres.repository.PostgresToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class AgentTraceRedactorTest {

  @Test
  void redactsCredentialHeadersTokensPasswordsAndSecretValues() {
    ObjectMapper mapper = new ObjectMapper();
    AgentTraceRedactor redactor = new AgentTraceRedactor(mapper);
    JsonNode input = mapper.valueToTree(Map.of(
        "Authorization", "Bearer abc.def.ghi",
        "openai_api_key", "sk-test",
        "oauthToken", "oauth-secret",
        "databasePassword", "db-secret",
        "headers", Map.of("Cookie", "SESSION=abc"),
        "normal", "keep"));

    JsonNode redacted = redactor.redact(input);

    assertThat(redacted.get("Authorization").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("openai_api_key").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("oauthToken").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("databasePassword").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("headers").get("Cookie").asText()).isEqualTo("[REDACTED]");
    assertThat(redacted.get("normal").asText()).isEqualTo("keep");
  }

  @Test
  void preservesNonSensitiveTokenBudgetAndUsageMetrics() {
    ObjectMapper mapper = new ObjectMapper();
    AgentTraceRedactor redactor = new AgentTraceRedactor(mapper);
    JsonNode input = mapper.valueToTree(Map.of(
        "promptTokenBudget", 8_000,
        "finalRequestTokenEstimate", 7_920,
        "inputTokens", 8_762,
        "cachedTokens", 640,
        "accessToken", "must-not-leak"));

    JsonNode redacted = redactor.redact(input);

    assertThat(redacted.get("promptTokenBudget").asInt()).isEqualTo(8_000);
    assertThat(redacted.get("finalRequestTokenEstimate").asInt()).isEqualTo(7_920);
    assertThat(redacted.get("inputTokens").asInt()).isEqualTo(8_762);
    assertThat(redacted.get("cachedTokens").asInt()).isEqualTo(640);
    assertThat(redacted.get("accessToken").asText()).isEqualTo("[REDACTED]");
  }

  @Test
  void redactsToolBlobContentAgainBeforePersistingIt() {
    ObjectMapper mapper = new ObjectMapper();
    CapturingBlobMapper blobMapper = new CapturingBlobMapper();
    PostgresToolResultStore store = new PostgresToolResultStore(blobMapper, new ToolCallMapper(), mapper);
    AgentLoopContext context = new AgentLoopContext(
        "run", new AgentRequest("run", "request", java.util.List.of(LlmMessage.user("question")),
        Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 17L)), 2, Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 17L));

    store.saveToolResult(
        context,
        1,
        new LlmToolCall("call", "lookup", mapper.createObjectNode()),
        mapper.createObjectNode().put("accessToken", "secret").put("value", "safe"),
        "{\"accessToken\":\"leaked\"}",
        "application/json",
        AgentTraceRedactor.POLICY_VERSION);

    assertThat(blobMapper.row.contentText()).contains("[REDACTED]", "safe").doesNotContain("secret", "leaked");
  }

  private static final class CapturingBlobMapper implements AgentContentBlobMapper {
    private ContentBlobInsertRow row;

    @Override
    public Long insertBlob(ContentBlobInsertRow row) {
      this.row = row;
      return 88L;
    }

    @Override
    public java.util.Optional<org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobRow> findById(long id) {
      return java.util.Optional.empty();
    }
  }

  private static final class ToolCallMapper implements AgentRunTraceMapper {
    @Override public int insertStepStart(org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepStartRow row) { return 0; }
    @Override public int attachRequestSnapshot(long runId, int stepIndex, long requestSnapshotId) { return 0; }
    @Override public int markStepSucceeded(org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepEndUpdate update) { return 0; }
    @Override public int markStepFailed(org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepErrorUpdate update) { return 0; }
    @Override public int insertToolStart(org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow row) { return 0; }
    @Override public int markToolSucceeded(org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallEndUpdate update) { return 0; }
    @Override public int markToolFailed(org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallErrorUpdate update) { return 0; }
    @Override public Long findToolCallDbId(long runId, int stepIndex, String toolCallId) { return 31L; }
    @Override public int updateToolResultStorage(ToolCallStorageUpdate update) { return 0; }
    @Override public Long findRunIdByResultBlobId(long blobId) { return null; }
    @Override public org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolResultProvenanceRow findToolResultProvenanceByBlobId(long blobId) { return null; }
  }
}
