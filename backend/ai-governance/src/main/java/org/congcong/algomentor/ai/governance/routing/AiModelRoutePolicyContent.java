package org.congcong.algomentor.ai.governance.routing;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;

/** 模型路由规则中唯一允许持久化的目标引用。 */
public record AiModelRoutePolicyContent(
    long modelId,
    @JsonInclude(JsonInclude.Include.NON_NULL) LlmReasoningEffort reasoningEffort
) {

  public AiModelRoutePolicyContent(long modelId) {
    this(modelId, null);
  }

  public AiModelRoutePolicyContent {
    if (modelId < 1) {
      throw new IllegalArgumentException("modelId must be positive");
    }
  }
}
