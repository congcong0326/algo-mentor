package org.congcong.algomentor.ai.governance.routing;

/** 模型路由规则中唯一允许持久化的目标引用。 */
public record AiModelRoutePolicyContent(long modelId) {
  public AiModelRoutePolicyContent {
    if (modelId < 1) {
      throw new IllegalArgumentException("modelId must be positive");
    }
  }
}
