package org.congcong.algomentor.llm.core.gateway;

import java.util.List;
import org.congcong.algomentor.llm.core.provider.LlmProvider;

/**
 * 根据运行时参数和已注册 provider 创建 LLM gateway。
 */
public interface LlmGatewayFactory {

  LlmGateway create(List<LlmProvider> providers, LlmGatewayOptions options);
}
