package org.congcong.algomentor.api.config;

/**
 * Spring 配置属性 key 和前缀。
 */
public final class MentorConfigurationKeys {

  /**
   * 默认 LLM gateway 配置前缀。
   */
  public static final String AI_GATEWAY_PREFIX = "algo-mentor.ai.gateway";

  /**
   * Agent 工具结果压缩配置前缀。
   */
  public static final String AGENT_COMPACTION_PREFIX = "algo-mentor.agent.compaction";

  /**
   * Agent 工具执行权限配置前缀。
   */
  public static final String AGENT_TOOL_PERMISSION_PREFIX = "algo-mentor.agent.tool-permission";

  /** Agent loop 专用执行线程池配置前缀。 */
  public static final String AGENT_EXECUTOR_PREFIX = "algo-mentor.agent.executor";

  /** 统一 Agent Runtime 装配开关配置前缀。 */
  public static final String AGENT_RUNTIME_PREFIX = "algo-mentor.agent.runtime";

  /** Practice Chat 独立 Redis Stream 通道配置前缀。 */
  public static final String PRACTICE_REALTIME_STREAM_PREFIX = "algo-mentor.practice-chat.realtime-stream";

  /** 学习计划首次草案生成独立 Redis Stream 通道配置前缀。 */
  public static final String LEARNING_PLAN_GENERATION_REALTIME_STREAM_PREFIX =
      "algo-mentor.learning-plan.generation.realtime-stream";

  /** 学习计划创建治理配置前缀。 */
  public static final String LEARNING_PLAN_GOVERNANCE_PREFIX = "algo-mentor.learning-plan.governance";

  /** 首次草案生成启动恢复配置前缀。 */
  public static final String LEARNING_PLAN_GENERATION_RECOVERY_PREFIX =
      LEARNING_PLAN_GOVERNANCE_PREFIX + ".generation-recovery";

  /** 学习画像文档与 statement ref 的配置前缀。 */
  public static final String LEARNER_PROFILE_DOCUMENT_PREFIX = "algo-mentor.learner-memory.profile-document";

  /**
   * API SSE 连接配置前缀。
   */
  public static final String API_SSE_PREFIX = "algo-mentor.api.sse";

  /** 用户提交内容的统一上限配置前缀。 */
  public static final String USER_INPUT_LIMITS_PREFIX = "algo-mentor.user-input-limits";

  /**
   * OpenAI provider 配置前缀。
   */
  public static final String OPENAI_PREFIX = "algo-mentor.ai.openai";

  /**
   * calculator 工具配置前缀。
   */
  public static final String CALCULATOR_TOOL_PREFIX = "algo-mentor.agent.tools.calculator";

  /**
   * 题库过滤项发现工具配置前缀。
   */
  public static final String PROBLEM_FILTERS_TOOL_PREFIX = "algo-mentor.agent.tools.problem-filters";

  /**
   * 题库搜索工具配置前缀。
   */
  public static final String PROBLEM_SEARCH_TOOL_PREFIX = "algo-mentor.agent.tools.problem-search";

  /**
   * 题面读取工具配置前缀。
   */
  public static final String PROBLEM_STATEMENT_TOOL_PREFIX = "algo-mentor.agent.tools.problem-statement";

  /**
   * Agent 工具选择模式配置 key。
   */
  public static final String AGENT_TOOL_CHOICE = "algo-mentor.agent.tool-choice";

  /**
   * specific 工具选择模式下的工具名配置 key。
   */
  public static final String AGENT_SPECIFIC_TOOL_NAME = "algo-mentor.agent.specific-tool-name";

  /**
   * Agent loop 最大步数配置 key。
   */
  public static final String AGENT_MAX_STEPS = "algo-mentor.agent.max-steps";

  /**
   * 开关型配置的字段名。
   */
  public static final String ENABLED = "enabled";

  /** 统一 Agent Runtime 装配开关 key。 */
  public static final String AGENT_RUNTIME_ENABLED = AGENT_RUNTIME_PREFIX + "." + ENABLED;

  /** 首次草案生成启动恢复开关 key。 */
  public static final String LEARNING_PLAN_GENERATION_RECOVERY_ENABLED =
      LEARNING_PLAN_GENERATION_RECOVERY_PREFIX + "." + ENABLED;

  /**
   * 开关型配置启用值。
   */
  public static final String TRUE = "true";

  private MentorConfigurationKeys() {
  }
}
