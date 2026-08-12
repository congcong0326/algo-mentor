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
   * 按 section 记录的预算动作，用于审计展示而不包含 section 正文。
   */
  public static final String PROMPT_SECTION_ACTIONS = "promptSectionActions";

  /**
   * Prompt section 内容 hash 映射。
   */
  public static final String PROMPT_CONTENT_HASHES = "promptContentHashes";

  /**
   * 脱敏后的 section 审计快照；仅包含来源、信任等级、预算决策和 hash，不包含正文。
   */
  public static final String PROMPT_SECTION_SNAPSHOTS = "promptSectionSnapshots";

  /**
   * 最终请求审计快照中消息的来源等级，不参与 provider 请求映射。
   */
  public static final String AUDIT_MESSAGE_SOURCE = "auditSource";

  /**
   * 最终请求审计快照中消息对应的 Prompt section 标识。
   */
  public static final String AUDIT_MESSAGE_SECTION_ID = "auditSectionId";

  private AgentPromptMetadataKeys() {
  }
}
