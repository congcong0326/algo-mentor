package org.congcong.algomentor.agent.core;

import org.congcong.algomentor.llm.core.model.LlmModelSelector;

/**
 * 解析单次 Agent LLM 请求应使用的模型选择器。
 */
@FunctionalInterface
public interface AgentModelSelectorResolver {

  LlmModelSelector resolve(AgentModelSelectionContext context);
}
