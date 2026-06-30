package org.congcong.algomentor.agent.core;

import org.congcong.algomentor.llm.core.model.LlmModelSelector;

/**
 * 默认模型选择策略：优先使用请求级显式模型，否则兜底到全局默认模型。
 */
public final class DefaultAgentModelSelectorResolver implements AgentModelSelectorResolver {

  @Override
  public LlmModelSelector resolve(AgentModelSelectionContext context) {
    if (context == null) {
      throw new IllegalArgumentException("Agent model selection context must not be null");
    }
    LlmModelSelector requestSelector = context.request().executionOptions().modelSelector();
    return requestSelector == null ? context.defaultSelector() : requestSelector;
  }
}
