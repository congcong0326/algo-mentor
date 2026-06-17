package org.congcong.algomentor.agent.core.compaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.core.toolresult.ToolResultRefs;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

/**
 * 工具结果压缩器，负责把工具真实返回值转换成“适合放进下一轮 LLM 上下文”的 JSON。
 *
 * <p>Agent 工具可能返回很大的 JSON，例如题库检索结果、代码执行日志、文档片段或向量召回内容。
 * 如果原样塞回 messages，会带来三个问题：上下文窗口被快速占满、每轮请求成本持续放大、模型被大量低价值细节干扰。
 * 因此这里采用两级策略：</p>
 *
 * <ul>
 *   <li>小结果直接 inline，保证模型看到完整数据，避免无意义的二次读取。</li>
 *   <li>大结果保存完整内容，只把 preview、resultRef 和结构摘要交给模型；模型需要更多细节时再调用
 *       {@code read_tool_result} 按范围读取。</li>
 * </ul>
 *
 * <p>传入的 {@code redactedResult} 约定已经由上游完成脱敏或裁剪。Compactor 不负责安全脱敏，只负责预算控制、
 * 引用生成和可观测 metadata 生成，这样职责边界更清晰。</p>
 */
public final class ToolResultCompactor {

  /**
   * 当前 compactor 只处理 JSON 树模型，序列化和存储时明确声明 content type，方便后续扩展纯文本、
   * markdown 或二进制附件引用。
   */
  public static final String CONTENT_TYPE_JSON = "application/json";

  private final ObjectMapper objectMapper;
  private final ToolResultCompactionPolicy policy;
  private final ToolResultStore resultStore;

  public ToolResultCompactor(ObjectMapper objectMapper, ToolResultCompactionPolicy policy, ToolResultStore resultStore) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.policy = policy == null ? ToolResultCompactionPolicy.defaults() : policy;
    this.resultStore = resultStore;
  }

  /**
   * 把单次工具调用结果压缩成下一轮模型可见的结果。
   *
   * <p>返回值包含两部分：</p>
   *
   * <ul>
   *   <li>{@code visibleResult}：真正写入 {@code LlmMessage.toolResult(...)} 的 JSON。</li>
   *   <li>{@code metadata}：给请求快照、观测和调试使用的压缩信息，例如存储模式、hash、字符数和策略版本。</li>
   * </ul>
   *
   * <p>判断阈值使用序列化后的字符数，而不是 {@link JsonNode#size()}。原因是 JSON 节点数量无法反映真实 token/字符预算：
   * 一个字段可能包含几万字文本，而一个数组也可能只是少量短字符串。序列化长度虽然不是精确 token 数，
   * 但稳定、便宜，并且足够作为上下文预算的近似值。</p>
   */
  public ToolResultCompaction compactForModel(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult
  ) {
    Objects.requireNonNull(toolCall, "toolCall must not be null");
    Objects.requireNonNull(redactedResult, "redactedResult must not be null");
    String serialized = canonicalJson(redactedResult);
    int charCount = serialized.length();
    int lineCount = lineCount(serialized);
    if (charCount <= policy.inlineMaxChars()) {
      // 小结果直接原样进入模型上下文。这样能保留完整语义，也避免模型为了一个很小的结果再发起 read_tool_result。
      return new ToolResultCompaction(
          redactedResult,
          new ToolResultCompactionMetadata(
              "inline",
              null,
              null,
              charCount,
              charCount,
              lineCount,
              sha256(serialized),
              CONTENT_TYPE_JSON,
              false,
              ToolResultCompactionPolicy.POLICY_VERSION));
    }

    // 大结果只给模型 preview。完整内容交给 ToolResultStore，后续可通过 resultRef 做有界范围读取。
    StoredToolResult stored = saveLargeResult(context, stepIndex, toolCall, redactedResult, serialized);
    int previewMax = Math.min(policy.previewMaxChars(), stored.contentText().length());
    String preview = stored.contentText().substring(0, previewMax);
    ObjectNode visible = objectMapper.createObjectNode();
    visible.put(AgentToolResultJsonKeys.TYPE, AgentToolResultTypes.PREVIEW);
    visible.put(AgentToolResultJsonKeys.RESULT_REF, stored.resultRef());
    visible.put(AgentToolResultJsonKeys.TOOL_CALL_ID, toolCall.id());
    visible.put(AgentToolResultJsonKeys.TOOL_NAME, toolCall.name());
    visible.put(AgentToolResultJsonKeys.CONTENT_TYPE, stored.contentType());
    visible.put(AgentToolResultJsonKeys.CHAR_COUNT, stored.charCount());
    visible.put(AgentToolResultJsonKeys.LINE_COUNT, stored.lineCount());
    visible.put(AgentToolResultJsonKeys.PREVIEW, preview);
    visible.put(AgentToolResultJsonKeys.TRUNCATED, true);
    addShape(visible, redactedResult);
    // readHint 是给模型看的操作提示。它把“为什么只看到 preview”和“如何继续读取”编码进上下文，
    // 减少模型在大结果场景下直接猜测缺失内容的概率。
    visible.put(
        AgentToolResultJsonKeys.READ_HINT,
        "Use read_tool_result with resultRef and offset/limit or line range if more detail is needed.");
    return new ToolResultCompaction(
        visible,
        new ToolResultCompactionMetadata(
            "blob",
            stored.resultRef(),
            stored.blobId(),
            preview.length(),
            stored.charCount(),
            stored.lineCount(),
            stored.sha256(),
            stored.contentType(),
            true,
            ToolResultCompactionPolicy.POLICY_VERSION));
  }

  /**
   * 生成旧工具结果被 run-level 压缩后的占位 JSON。
   *
   * <p>{@link RunMessageCompactor} 在请求前可能会把更早的 tool result 替换成占位，但不能直接删除。
   * 直接删除会破坏 assistant tool_calls 与 tool result 的配对关系，导致下一轮 LLM 上下文不符合 tool-calling 协议。
   * 占位消息保留 {@code toolCallId}/{@code toolName}/{@code resultRef}，让模型知道历史上发生过这次工具交互，
   * 必要时仍可通过 {@code read_tool_result} 读取范围内容。</p>
   */
  public JsonNode compactedPlaceholder(String toolCallId, String toolName, String resultRef) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put(AgentToolResultJsonKeys.TYPE, AgentToolResultTypes.COMPACTED);
    if (resultRef != null && !resultRef.isBlank()) {
      node.put(AgentToolResultJsonKeys.RESULT_REF, resultRef);
    }
    if (toolCallId != null && !toolCallId.isBlank()) {
      node.put(AgentToolResultJsonKeys.TOOL_CALL_ID, toolCallId);
    }
    if (toolName != null && !toolName.isBlank()) {
      node.put(AgentToolResultJsonKeys.TOOL_NAME, toolName);
    }
    node.put(AgentToolResultJsonKeys.MESSAGE, "Earlier tool result compacted. Re-read a range if needed.");
    node.put(AgentToolResultJsonKeys.TRUNCATED, true);
    return node;
  }

  /**
   * 暴露当前策略，供 run-level 压缩器复用同一组预算参数，避免单结果压缩和整轮消息压缩使用不同口径。
   */
  public ToolResultCompactionPolicy policy() {
    return policy;
  }

  /**
   * 保存大结果，或者在未启用 blob/store 不可用时生成确定性 fallback 引用。
   *
   * <p>fallback 分支不会真的提供可读取的持久 blob，但仍然返回一个基于 hash 的稳定 {@code resultRef}。
   * 这样模型可见结构和 metadata 仍保持一致，测试或轻量运行环境也不必强制配置持久化组件。</p>
   */
  private StoredToolResult saveLargeResult(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode redactedResult,
      String serialized
  ) {
    if (policy.blobEnabled() && resultStore != null) {
      return resultStore.saveToolResult(
          context,
          stepIndex,
          toolCall,
          redactedResult,
          serialized,
          CONTENT_TYPE_JSON,
          ToolResultCompactionPolicy.POLICY_VERSION);
    }
    String fallbackRef = ToolResultRefs.PREFIX + sha256(serialized).substring(0, 24);
    return new StoredToolResult(
        fallbackRef,
        CONTENT_TYPE_JSON,
        serialized,
        sha256(serialized),
        serialized.length(),
        lineCount(serialized),
        null,
        null);
  }

  /**
   * 给大结果 preview 添加轻量结构摘要。
   *
   * <p>preview 只截取前 N 个字符，可能刚好截在低价值字段上。顶层 keys 或数组长度可以帮助模型判断结果形态：
   * 例如它能知道完整结果里有哪些字段、是否是列表，以及是否需要继续读取某个范围。</p>
   */
  private void addShape(ObjectNode visible, JsonNode result) {
    if (result.isObject()) {
      ArrayNode keys = objectMapper.createArrayNode();
      result.fieldNames().forEachRemaining(keys::add);
      visible.set(AgentToolResultJsonKeys.TOP_LEVEL_KEYS, keys);
      return;
    }
    if (result.isArray()) {
      visible.put(AgentToolResultJsonKeys.ARRAY_LENGTH, result.size());
    }
  }

  /**
   * 将 JSON 树序列化为稳定文本，用于预算估算、preview、hash 和持久化。
   *
   * <p>这里不手写字符串拼接，而是统一走 Jackson，避免字段转义、数组、嵌套对象等 JSON 细节出错。</p>
   */
  private String canonicalJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize tool result JSON", ex);
    }
  }

  /**
   * 计算结果内容 hash，用于去重、审计和校验。
   *
   * <p>hash 基于脱敏后的序列化内容，而不是原始工具输出，避免 metadata 间接绑定敏感原文。</p>
   */
  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is unavailable", ex);
    }
  }

  /**
   * 统计文本行数，供 preview 和范围读取提示使用。
   *
   * <p>行数不是 JSON 语义的一部分，但对日志、执行输出、搜索结果等长文本很有用；模型可以据此决定使用 offset
   * 读取还是按 line range 读取。</p>
   */
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
