package org.congcong.algomentor.agent.core.compaction;

/**
 * 工具结果和 run 内消息压缩策略。
 *
 * <p>这里集中放预算参数，是为了让“单个工具结果如何进入上下文”和“整个 run 的 messages 如何裁剪”
 * 使用同一套口径。否则单结果压缩可能认为某个 preview 合理，但 run-level 压缩又用另一套阈值把它过早裁掉。</p>
 *
 * @param inlineMaxChars 单个工具结果可直接 inline 到 LLM 上下文的最大字符数
 * @param previewMaxChars 大结果生成 preview 时最多暴露给模型的字符数
 * @param rangeReadMaxChars 模型通过 read_tool_result 单次范围读取的最大字符数
 * @param blobEnabled 是否允许把大结果写入 ToolResultStore 并返回 resultRef
 * @param toolResultsTotalMaxChars 单次 LLM 请求中所有 tool result 可见内容的总字符预算
 * @param keepRecentToolResults run-level 压缩时保留最近多少个工具结果
 * @param compactOldToolResults 是否允许把旧工具结果替换成占位
 * @param inputTokenBudget 估算的输入 token 预算，用于整体上下文裁剪
 * @param maxMessageGroups run-level 压缩时最多保留的消息组数量
 * @param snipKeepHeadGroups 整体上下文过长时，头部保留的消息组数量
 * @param snipKeepTailGroups 整体上下文过长时，尾部保留的消息组数量
 * @param groupAwareSnipEnabled 是否按消息组裁剪，避免拆散 assistant tool_calls 和 tool result
 * @param llmCompactEnabled 是否启用后续基于 LLM 的摘要压缩能力
 */
public record ToolResultCompactionPolicy(
    int inlineMaxChars,
    int previewMaxChars,
    int rangeReadMaxChars,
    boolean blobEnabled,
    int toolResultsTotalMaxChars,
    int keepRecentToolResults,
    boolean compactOldToolResults,
    int inputTokenBudget,
    int maxMessageGroups,
    int snipKeepHeadGroups,
    int snipKeepTailGroups,
    boolean groupAwareSnipEnabled,
    boolean llmCompactEnabled
) {

  public static final String POLICY_VERSION = "tool-result-compaction-v1";

  /**
   * 校验策略参数，尽早阻止无效预算进入运行态。
   *
   * <p>这些值一旦非法，后续可能表现为 substring 越界、无限读取、所有消息被裁空等更难定位的问题；
   * 在 record 构造期失败可以让配置错误更快暴露。</p>
   */
  public ToolResultCompactionPolicy {
    if (inlineMaxChars < 1) {
      throw new IllegalArgumentException("inlineMaxChars must be positive");
    }
    if (previewMaxChars < 1) {
      throw new IllegalArgumentException("previewMaxChars must be positive");
    }
    if (rangeReadMaxChars < 1) {
      throw new IllegalArgumentException("rangeReadMaxChars must be positive");
    }
    if (toolResultsTotalMaxChars < 1) {
      throw new IllegalArgumentException("toolResultsTotalMaxChars must be positive");
    }
    if (keepRecentToolResults < 0) {
      throw new IllegalArgumentException("keepRecentToolResults must not be negative");
    }
    if (inputTokenBudget < 1) {
      throw new IllegalArgumentException("inputTokenBudget must be positive");
    }
    if (maxMessageGroups < 1) {
      throw new IllegalArgumentException("maxMessageGroups must be positive");
    }
    if (snipKeepHeadGroups < 0) {
      throw new IllegalArgumentException("snipKeepHeadGroups must not be negative");
    }
    if (snipKeepTailGroups < 1) {
      throw new IllegalArgumentException("snipKeepTailGroups must be positive");
    }
  }

  /**
   * 默认策略偏向保守：小结果直接 inline，大结果 preview + blob 引用，旧结果允许按消息组压缩。
   *
   * <p>默认不开启 LLM 摘要压缩，因为那会引入额外模型调用、成本和失败路径；第一阶段先使用确定性压缩策略。</p>
   */
  public static ToolResultCompactionPolicy defaults() {
    return new ToolResultCompactionPolicy(
        12_000,
        2_000,
        8_000,
        true,
        60_000,
        3,
        true,
        120_000,
        80,
        2,
        24,
        true,
        false);
  }

  /**
   * 用经验值把 token 预算换算成字符预算。
   *
   * <p>不同模型 tokenizer 不完全一致，精确 token 计算应放到 provider 或专门 tokenizer 中。
   * 这里使用 1 token 约 4 字符的粗估值，目的是在 agent-core 层做便宜、稳定的上限保护。</p>
   */
  public int estimatedInputCharBudget() {
    return inputTokenBudget * 4;
  }
}
