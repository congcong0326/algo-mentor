package org.congcong.algomentor.agent.core.runtime.model;

/**
 * Agent 工具结果在 LLM 上下文、持久化快照和范围读取工具之间共享的 JSON 字段名。
 */
public final class AgentToolResultJsonKeys {

  /**
   * 工具结果载荷类型字段，用于区分 preview、range、compacted 等不同结构。
   */
  public static final String TYPE = "type";

  /**
   * 可重新读取完整工具结果或片段的引用。
   */
  public static final String RESULT_REF = "resultRef";

  /**
   * LLM 生成的工具调用 ID，用于把 tool message 对回 assistant tool_calls。
   */
  public static final String TOOL_CALL_ID = "toolCallId";

  /**
   * 工具注册名称，用于排查和让模型理解结果来自哪个工具。
   */
  public static final String TOOL_NAME = "toolName";

  /**
   * 工具结果内容类型，例如 application/json。
   */
  public static final String CONTENT_TYPE = "contentType";

  /**
   * 结果内容或片段的字符数。
   */
  public static final String CHAR_COUNT = "charCount";

  /**
   * 结果内容的行数，主要用于长文本按行读取。
   */
  public static final String LINE_COUNT = "lineCount";

  /**
   * 大结果放入上下文的前缀预览。
   */
  public static final String PREVIEW = "preview";

  /**
   * 表示当前结果不是完整内容。
   */
  public static final String TRUNCATED = "truncated";

  /**
   * 给模型的读取提示，说明如何用 read_tool_result 继续获取细节。
   */
  public static final String READ_HINT = "readHint";

  /**
   * 范围读取返回的读取区间描述对象。
   */
  public static final String RANGE = "range";

  /**
   * 按字符读取时的起始偏移。
   */
  public static final String OFFSET = "offset";

  /**
   * 按字符读取时的最大读取长度。
   */
  public static final String LIMIT = "limit";

  /**
   * 按行读取时的起始行号，从 1 开始。
   */
  public static final String LINE_START = "lineStart";

  /**
   * 按行读取时的结束行号，从 1 开始。
   */
  public static final String LINE_END = "lineEnd";

  /**
   * 当前片段之前是否还有更多内容。
   */
  public static final String HAS_MORE_BEFORE = "hasMoreBefore";

  /**
   * 当前片段之后是否还有更多内容。
   */
  public static final String HAS_MORE_AFTER = "hasMoreAfter";

  /**
   * 范围读取返回的实际内容。
   */
  public static final String CONTENT = "content";

  /**
   * 大结果 preview 暴露的顶层字段列表。
   */
  public static final String TOP_LEVEL_KEYS = "topLevelKeys";

  /**
   * 大结果 preview 暴露的数组长度。
   */
  public static final String ARRAY_LENGTH = "arrayLength";

  /**
   * 占位消息或 snip 标记中的说明文本。
   */
  public static final String MESSAGE = "message";

  private AgentToolResultJsonKeys() {
  }
}
