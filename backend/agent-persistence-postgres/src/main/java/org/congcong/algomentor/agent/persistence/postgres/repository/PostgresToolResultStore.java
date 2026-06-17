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
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobInsertRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public class PostgresToolResultStore implements ToolResultStore {

  private static final String REF_PREFIX = "tool-result:";

  private final AgentContentBlobMapper blobMapper;
  private final AgentRunTraceMapper traceMapper;
  private final ObjectMapper objectMapper;
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
    Long runDbId = longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
    if (runDbId == null) {
      return fallback(serializedResult, contentType);
    }
    Long toolCallDbId = traceMapper.findToolCallDbId(runDbId, stepIndex, toolCall.id());
    if (toolCallDbId == null) {
      return fallback(serializedResult, contentType);
    }
    String text = serializedResult == null ? "" : serializedResult;
    String sha256 = sha256(text);
    ContentBlobInsertRow row = new ContentBlobInsertRow(
        null,
        "tool_result",
        toolCallDbId,
        contentType,
        "postgres_text",
        text,
        null,
        null,
        sha256,
        text.length(),
        (long) text.getBytes(StandardCharsets.UTF_8).length,
        lineCount(text),
        redactionPolicyVersion,
        objectMapper.valueToTree(java.util.Map.of(
            "agentRunId", context.runId(),
            "stepIndex", stepIndex,
            "toolCallId", toolCall.id(),
            "toolName", toolCall.name())),
        clock.instant());
    Long blobId = blobMapper.insertBlob(row);
    String resultRef = REF_PREFIX + blobId;
    StoredToolResult stored = new StoredToolResult(
        resultRef,
        contentType,
        text,
        sha256,
        text.length(),
        lineCount(text),
        blobId,
        toolCallDbId);
    traceMapper.updateToolResultStorage(new ToolCallStorageUpdate(
        runDbId,
        stepIndex,
        toolCall.id(),
        "blob",
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
    if (context != null) {
      Long expectedRunId = longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
      Long actualRunId = traceMapper.findRunIdByResultBlobId(blobId);
      if (expectedRunId != null && actualRunId != null && !expectedRunId.equals(actualRunId)) {
        return Optional.empty();
      }
    }
    return blobMapper.findById(blobId).map(this::toStored);
  }

  private StoredToolResult toStored(ContentBlobRow row) {
    return new StoredToolResult(
        REF_PREFIX + row.id(),
        row.contentType(),
        row.contentText() == null ? "" : row.contentText(),
        row.sha256(),
        row.charCount() == null ? 0 : row.charCount(),
        row.lineCount() == null ? 0 : row.lineCount(),
        row.id(),
        row.scopeId());
  }

  private StoredToolResult fallback(String serializedResult, String contentType) {
    String text = serializedResult == null ? "" : serializedResult;
    return new StoredToolResult(
        REF_PREFIX + sha256(text).substring(0, 24),
        contentType,
        text,
        sha256(text),
        text.length(),
        lineCount(text),
        null,
        null);
  }

  private Long parseBlobId(String resultRef) {
    if (resultRef == null || !resultRef.startsWith(REF_PREFIX)) {
      return null;
    }
    try {
      return Long.parseLong(resultRef.substring(REF_PREFIX.length()));
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
