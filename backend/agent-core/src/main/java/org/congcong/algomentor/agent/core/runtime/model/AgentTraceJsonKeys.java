package org.congcong.algomentor.agent.core.runtime.model;

/**
 * Agent 请求快照和 trace JSON 中共享的字段名。
 */
public final class AgentTraceJsonKeys {

  /**
   * 模型选择器快照。
   */
  public static final String MODEL_SELECTOR = "modelSelector";

  /**
   * provider id 字段。
   */
  public static final String PROVIDER_ID = "providerId";

  /**
   * model id 字段。
   */
  public static final String MODEL_ID = "modelId";

  /**
   * 模型调用目的。
   */
  public static final String PURPOSE = "purpose";

  /**
   * 所需模型能力列表。
   */
  public static final String REQUIRED_CAPABILITIES = "requiredCapabilities";

  /**
   * LLM messages 快照。
   */
  public static final String MESSAGES = "messages";

  /**
   * LLM tools 快照。
   */
  public static final String TOOLS = "tools";

  /**
   * LLM tool choice 快照。
   */
  public static final String TOOL_CHOICE = "toolChoice";

  /**
   * LLM generation options 快照。
   */
  public static final String GENERATION_OPTIONS = "generationOptions";

  /**
   * LLM response format 快照。
   */
  public static final String RESPONSE_FORMAT = "responseFormat";

  /**
   * 请求 metadata 快照。
   */
  public static final String METADATA = "metadata";

  private AgentTraceJsonKeys() {
  }
}
