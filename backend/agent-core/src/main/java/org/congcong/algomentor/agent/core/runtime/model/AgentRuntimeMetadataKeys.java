package org.congcong.algomentor.agent.core.runtime.model;

public final class AgentRuntimeMetadataKeys {

  /**
   * Agent 任务数据库 ID。
   */
  public static final String TASK_ID = "taskId";

  /**
   * Agent 轮次数据库 ID。
   */
  public static final String TURN_ID = "turnId";

  /**
   * Agent run 数据库 ID。
   */
  public static final String RUN_DB_ID = "runDbId";

  /**
   * 当前上下文允许使用的 token 预算。
   */
  public static final String TOKEN_BUDGET = "tokenBudget";

  /**
   * 当前上下文的 token 估算值。
   */
  public static final String TOKEN_ESTIMATE = "tokenEstimate";

  /**
   * 上下文组装策略名称。
   */
  public static final String CONTEXT_POLICY = "contextPolicy";

  /**
   * 上下文组装策略版本。
   */
  public static final String CONTEXT_POLICY_VERSION = "contextPolicyVersion";

  /**
   * 业务适配器标识，用于区分 topic explanation、conversation 等入口。
   */
  public static final String ADAPTER = "adapter";

  /**
   * 学习主题原始标题。
   */
  public static final String TITLE = "title";

  /**
   * 学习主题标题，保留给调用链中需要明确语义的 metadata。
   */
  public static final String TOPIC_TITLE = "topicTitle";

  /**
   * Agent run 字符串 ID，用于日志、trace metadata 和跨层关联。
   */
  public static final String AGENT_RUN_ID = "agentRunId";

  /**
   * 工具调用 ID，用于错误、trace 和工具结果 metadata。
   */
  public static final String TOOL_CALL_ID = "toolCallId";

  /**
   * 工具名称，用于错误、trace 和工具结果 metadata。
   */
  public static final String TOOL_NAME = "toolName";

  /**
   * 直接抛出异常的 Java 类型，用于工具失败诊断。
   */
  public static final String ERROR_TYPE = "errorType";

  /**
   * 直接抛出异常的错误消息，用于工具失败诊断。
   */
  public static final String ERROR_MESSAGE = "errorMessage";

  /**
   * 直接 cause 的 Java 类型，用于工具失败诊断。
   */
  public static final String CAUSE_TYPE = "causeType";

  /**
   * 直接 cause 的错误消息，用于工具失败诊断。
   */
  public static final String CAUSE_MESSAGE = "causeMessage";

  /**
   * 异常链最底层根因的 Java 类型，用于工具失败诊断。
   */
  public static final String ROOT_CAUSE_TYPE = "rootCauseType";

  /**
   * 异常链最底层根因的错误消息，用于工具失败诊断。
   */
  public static final String ROOT_CAUSE_MESSAGE = "rootCauseMessage";

  /**
   * 幂等键命中已有 run 时的 replay 标记。
   */
  public static final String IDEMPOTENT_REPLAY = "idempotentReplay";

  /**
   * Agent run 被取消时记录的取消来源。
   */
  public static final String CANCELLATION_REASON = "cancellationReason";

  /**
   * 请求 metadata 在 trace 快照中的字段名。
   */
  public static final String REQUEST_METADATA = "requestMetadata";

  /**
   * run 内上下文压缩 metadata 的根字段名。
   */
  public static final String RUN_CONTEXT_COMPACTION = "runContextCompaction";

  /**
   * 上下文压缩策略版本。
   */
  public static final String POLICY_VERSION = "policyVersion";

  /**
   * 压缩前可见字符数。
   */
  public static final String BEFORE_CHAR_COUNT = "beforeCharCount";

  /**
   * 压缩后可见字符数。
   */
  public static final String AFTER_CHAR_COUNT = "afterCharCount";

  /**
   * 压缩前消息分组数量。
   */
  public static final String BEFORE_GROUP_COUNT = "beforeGroupCount";

  /**
   * 压缩后消息分组数量。
   */
  public static final String AFTER_GROUP_COUNT = "afterGroupCount";

  /**
   * 当前工具结果总字符数。
   */
  public static final String TOOL_RESULT_CHAR_COUNT = "toolResultCharCount";

  /**
   * 被压缩的工具调用 ID 列表。
   */
  public static final String COMPACTED_TOOL_CALL_IDS = "compactedToolCallIds";

  /**
   * 被压缩的工具结果数量。
   */
  public static final String COMPACTED_TOOL_RESULTS = "compactedToolResults";

  /**
   * 被 snip 的消息分组数量。
   */
  public static final String SNIPPED_GROUPS = "snippedGroups";

  /**
   * 因缺少安全候选分组而跳过 snip 的标记。
   */
  public static final String SNIP_SKIPPED = "snipSkipped";

  /**
   * 触发 max steps 熔断时记录的步数上限。
   */
  public static final String MAX_STEPS = "maxSteps";

  /**
   * 结构化输出 schema 名称。
   */
  public static final String SCHEMA_NAME = "schemaName";

  /**
   * 结构化输出 schema 版本。
   */
  public static final String SCHEMA_VERSION = "schemaVersion";

  /**
   * 最终输出解析策略。
   */
  public static final String STRUCTURED_OUTPUT_STRATEGY = "structuredOutputStrategy";

  /**
   * 最终输出字符数。
   */
  public static final String OUTPUT_CHAR_COUNT = "outputCharCount";

  /**
   * 结构化输出 JSON 解析错误消息。
   */
  public static final String PARSE_ERROR = "parseError";

  private AgentRuntimeMetadataKeys() {
  }
}
