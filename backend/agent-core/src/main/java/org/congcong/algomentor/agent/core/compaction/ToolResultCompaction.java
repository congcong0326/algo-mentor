package org.congcong.algomentor.agent.core.compaction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * 单个工具结果压缩后的输出。
 *
 * <p>{@code visibleResult} 是会写回下一轮 {@code LlmMessage.toolResult(...)} 的 JSON，
 * 也就是模型真正能看到的内容；{@code metadata} 不直接给模型推理使用，主要用于请求快照、持久化、观测和调试。</p>
 *
 * @param visibleResult 模型可见的工具结果，可能是完整结果，也可能是 preview/占位结构
 * @param metadata 压缩过程产生的存储模式、hash、字符数、是否截断等信息
 */
public record ToolResultCompaction(
    JsonNode visibleResult,
    ToolResultCompactionMetadata metadata
) {

  /**
   * record 构造期做非空校验，避免后续 AgentLoopRunner 追加 tool result message 时才出现难定位的空指针。
   */
  public ToolResultCompaction {
    if (visibleResult == null) {
      throw new IllegalArgumentException("visibleResult must not be null");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
  }

  /**
   * 转成可挂到 LLM 请求 metadata 上的结构。
   *
   * <p>这样 observer、持久化快照或 provider 日志可以看到本次请求使用了 inline 还是 blob preview，
   * 以及是否发生过截断，而不需要解析模型可见的 JSON 内容。</p>
   */
  public Map<String, Object> requestMetadata() {
    return metadata.asMap();
  }
}
