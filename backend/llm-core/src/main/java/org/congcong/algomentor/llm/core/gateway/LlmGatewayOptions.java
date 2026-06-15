package org.congcong.algomentor.llm.core.gateway;

import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;

/**
 * 创建 LLM gateway 时使用的默认选择参数。
 */
public record LlmGatewayOptions(
    LlmProviderId defaultProviderId,
    LlmModelId defaultModelId
) {

  public LlmGatewayOptions {
    if (defaultProviderId == null) {
      throw new IllegalArgumentException("LLM gateway default provider id must not be null");
    }
    if (defaultModelId == null) {
      throw new IllegalArgumentException("LLM gateway default model id must not be null");
    }
  }

  public LlmModelSelector defaultSelector(String purpose) {
    return new LlmModelSelector(defaultProviderId, defaultModelId, null, purpose);
  }
}
