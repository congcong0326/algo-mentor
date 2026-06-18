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

  /**
   * 开关型配置启用值。
   */
  public static final String TRUE = "true";

  private MentorConfigurationKeys() {
  }
}
