package org.congcong.algomentor.llm.core.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 可出现在聊天消息中的类型化内容片段。
 */
public sealed interface LlmContentPart
    permits LlmContentPart.Text, LlmContentPart.Image, LlmContentPart.File, LlmContentPart.ToolResult, LlmContentPart.Custom {

  /**
   * Plain text message content.
   */
  record Text(String text) implements LlmContentPart {
    public Text {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("LLM text content must not be blank");
      }
    }
  }

  /**
   * Image content referenced by URL or inline data.
   */
  record Image(String url, String base64Data, String mediaType) implements LlmContentPart {
    public Image {
      if ((url == null || url.isBlank()) && (base64Data == null || base64Data.isBlank())) {
        throw new IllegalArgumentException("LLM image content must include url or base64 data");
      }
    }
  }

  /**
   * Provider-managed file reference content.
   */
  record File(String fileId, String fileName, String mediaType) implements LlmContentPart {
    public File {
      if (fileId == null || fileId.isBlank()) {
        throw new IllegalArgumentException("LLM file content must include file id");
      }
    }
  }

  /**
   * Structured result content returned by a tool execution.
   */
  record ToolResult(JsonNode result) implements LlmContentPart {
    public ToolResult {
      if (result == null) {
        throw new IllegalArgumentException("LLM tool result content must not be null");
      }
    }
  }

  /**
   * Extension point for provider-specific content payloads.
   */
  record Custom(String type, Map<String, Object> payload) implements LlmContentPart {
    public Custom {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("LLM custom content type must not be blank");
      }
      payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
  }
}
