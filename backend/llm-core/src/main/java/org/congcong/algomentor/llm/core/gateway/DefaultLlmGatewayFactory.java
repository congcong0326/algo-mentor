package org.congcong.algomentor.llm.core.gateway;

import java.util.List;
import org.congcong.algomentor.llm.core.provider.LlmProvider;

/**
 * 默认工厂实现，复用 DefaultLlmGateway 的 provider/model 校验与分发逻辑。
 */
public class DefaultLlmGatewayFactory implements LlmGatewayFactory {

  @Override
  public LlmGateway create(List<LlmProvider> providers, LlmGatewayOptions options) {
    if (options == null) {
      throw new IllegalArgumentException("LLM gateway options must not be null");
    }
    return new DefaultLlmGateway(providers, options.defaultProviderId(), options.defaultModelId());
  }
}
