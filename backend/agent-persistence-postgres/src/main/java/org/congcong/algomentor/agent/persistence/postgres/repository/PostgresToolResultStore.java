package org.congcong.algomentor.agent.persistence.postgres.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultRefs;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobInsertRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolResultProvenanceRow;
import org.congcong.algomentor.agent.persistence.postgres.observer.AgentTraceRedactor;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public class PostgresToolResultStore implements ToolResultStore {

  private static final String BLOB_SCOPE_TYPE_TOOL_RESULT = "tool_result";
  private static final String BLOB_STORAGE_MODE_POSTGRES_TEXT = "postgres_text";
  private static final String RESULT_STORAGE_MODE_BLOB = "blob";

  private final AgentContentBlobMapper blobMapper;
  private final AgentRunTraceMapper traceMapper;
  private final ObjectMapper objectMapper;
  private final AgentTraceRedactor redactor;
  private final Clock clock;

  public PostgresToolResultStore(
      AgentContentBlobMapper blobMapper,
      AgentRunTraceMapper traceMapper,
      ObjectMapper objectMapper
  ) {
    this(blobMapper, traceMapper, objectMapper, Clock.systemUTC());
  }

  public PostgresToolResultStore(
      AgentContentBlobMapper blobMapper,
      AgentRunTraceMapper traceMapper,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.blobMapper = blobMapper;
    this.traceMapper = traceMapper;
    this.objectMapper = objectMapper;
    this.redactor = new AgentTraceRedactor(objectMapper);
    this.clock = clock;
  }

  @Override
  public StoredToolResult saveToolResult(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult,
      String serializedResult,
      String contentType,
      String redactionPolicyVersion
  ) {
    String text = serializedRedactedResult(redactedResult);
    Long runDbId = longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
    if (runDbId == null) {
      return fallback(text, contentType);
    }
    Long toolCallDbId = traceMapper.findToolCallDbId(runDbId, stepIndex, toolCall.id());
    if (toolCallDbId == null) {
      return fallback(text, contentType);
    }
    String sha256 = sha256(text);
    ContentBlobInsertRow row = new ContentBlobInsertRow(
        null,
        BLOB_SCOPE_TYPE_TOOL_RESULT,
        toolCallDbId,
        contentType,
        BLOB_STORAGE_MODE_POSTGRES_TEXT,
        text,
        null,
        null,
        sha256,
        text.length(),
        (long) text.getBytes(StandardCharsets.UTF_8).length,
        lineCount(text),
        redactionPolicyVersion,
        objectMapper.valueToTree(java.util.Map.of(
            AgentRuntimeMetadataKeys.AGENT_RUN_ID, context.runId(),
            "stepIndex", stepIndex,
            AgentRuntimeMetadataKeys.TOOL_CALL_ID, toolCall.id(),
            AgentRuntimeMetadataKeys.TOOL_NAME, toolCall.name())),
        clock.instant());
    Long blobId = blobMapper.insertBlob(row);
    String resultRef = ToolResultRefs.PREFIX + blobId;
    StoredToolResult stored = new StoredToolResult(
        resultRef,
        contentType,
        text,
        sha256,
        text.length(),
        lineCount(text),
        blobId,
        toolCallDbId,
        new ToolResultProvenance(stepIndex, toolCall.id(), toolCall.name()));
    traceMapper.updateToolResultStorage(new ToolCallStorageUpdate(
        runDbId,
        stepIndex,
        toolCall.id(),
        RESULT_STORAGE_MODE_BLOB,
        blobId,
        null,
        resultRef,
        stored.lineCount(),
        stored.sha256()));
    return stored;
  }

  @Override
  public Optional<StoredToolResult> findByResultRef(AgentLoopContext context, String resultRef) {
    Long blobId = parseBlobId(resultRef);
    if (blobId == null) {
      return Optional.empty();
    }
    Long expectedRunId = longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
    if (expectedRunId == null) {
      return Optional.empty();
    }
    Long actualRunId = traceMapper.findRunIdByResultBlobId(blobId);
    if (!expectedRunId.equals(actualRunId)) {
      return Optional.empty();
    }
    ToolResultProvenanceRow provenance = traceMapper.findToolResultProvenanceByBlobId(blobId);
    if (provenance == null) {
      return Optional.empty();
    }
    return blobMapper.findById(blobId).map(row -> toStored(row, provenance));
  }

  private StoredToolResult toStored(ContentBlobRow row, ToolResultProvenanceRow provenance) {
    return new StoredToolResult(
        ToolResultRefs.PREFIX + row.id(),
        row.contentType(),
        row.contentText() == null ? "" : row.contentText(),
        row.sha256(),
        row.charCount() == null ? 0 : row.charCount(),
        row.lineCount() == null ? 0 : row.lineCount(),
        row.id(),
        row.scopeId(),
        new ToolResultProvenance(provenance.stepIndex(), provenance.toolCallId(), provenance.toolName()));
  }

  private StoredToolResult fallback(String serializedResult, String contentType) {
    String text = serializedResult == null ? "" : serializedResult;
    return new StoredToolResult(
        ToolResultRefs.PREFIX + sha256(text).substring(0, 24),
        contentType,
        text,
        sha256(text),
        text.length(),
        lineCount(text),
        null,
        null,
        ToolResultProvenance.unknown());
  }

  private String serializedRedactedResult(JsonNode result) {
    try {
      return objectMapper.writeValueAsString(redactor.redact(result));
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize redacted tool result", exception);
    }
  }

  private Long parseBlobId(String resultRef) {
    if (resultRef == null || !resultRef.startsWith(ToolResultRefs.PREFIX)) {
      return null;
    }
    try {
      return Long.parseLong(resultRef.substring(ToolResultRefs.PREFIX.length()));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private Long longMetadata(AgentLoopContext context, String key) {
    if (context == null || context.metadata() == null) {
      return null;
    }
    Object value = context.metadata().get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return null;
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is unavailable", ex);
    }
  }

  private int lineCount(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int lines = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        lines++;
      }
    }
    return lines;
  }
}
