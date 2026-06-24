package org.congcong.algomentor.agent.core.prompt;

public final class AgentPromptMetadataKeys {

  /**
   * Prompt profile 标识。
   */
  public static final String PROMPT_PROFILE = "promptProfile";

  /**
   * Prompt profile 版本。
   */
  public static final String PROMPT_PROFILE_VERSION = "promptProfileVersion";

  /**
   * Prompt section 版本映射。
   */
  public static final String PROMPT_SECTION_VERSIONS = "promptSectionVersions";

  /**
   * Prompt 组装策略名称。
   */
  public static final String PROMPT_POLICY = "promptPolicy";

  /**
   * Prompt 组装策略版本。
   */
  public static final String PROMPT_POLICY_VERSION = "promptPolicyVersion";

  /**
   * Prompt 输入 token 预算。
   */
  public static final String PROMPT_TOKEN_BUDGET = "promptTokenBudget";

  /**
   * Prompt 输入 token 估算值。
   */
  public static final String PROMPT_TOKEN_ESTIMATE = "promptTokenEstimate";

  /**
   * 被裁剪、提取、摘要或丢弃的 section id。
   */
  public static final String PROMPT_TRUNCATED_SECTIONS = "promptTruncatedSections";

  /**
   * Prompt section 内容 hash 映射。
   */
  public static final String PROMPT_CONTENT_HASHES = "promptContentHashes";

  private AgentPromptMetadataKeys() {
  }
}
