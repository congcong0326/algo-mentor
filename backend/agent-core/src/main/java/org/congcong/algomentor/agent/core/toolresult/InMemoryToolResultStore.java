package org.congcong.algomentor.agent.core.toolresult;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public final class InMemoryToolResultStore implements ToolResultStore {

  private final AtomicLong sequence = new AtomicLong();
  private final Map<String, StoredToolResult> byRef = new ConcurrentHashMap<>();

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
    String text = serializedResult == null ? "" : serializedResult;
    String resultRef = ToolResultRefs.PREFIX + sequence.incrementAndGet();
    StoredToolResult stored = new StoredToolResult(
        resultRef,
        contentType,
        text,
        sha256(text),
        text.length(),
        lineCount(text),
        null,
        null);
    byRef.put(resultRef, stored);
    return stored;
  }

  @Override
  public Optional<StoredToolResult> findByResultRef(AgentLoopContext context, String resultRef) {
    if (resultRef == null || resultRef.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byRef.get(resultRef));
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
    if (text.isEmpty()) {
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
