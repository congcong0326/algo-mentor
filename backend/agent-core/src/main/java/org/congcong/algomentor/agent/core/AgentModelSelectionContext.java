package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

/**
 * 单次 LLM 请求的模型选择上下文。
 *
 * <p>这里只承载可信运行时信息，不实现具体路由规则。后续按用户、场景、预算或实验分组切换模型时，
 * 可以通过替换 {@link AgentModelSelectorResolver} 使用这些字段做决策。</p>
 */
public record AgentModelSelectionContext(
    AgentRequest request,
    int stepIndex,
    List<LlmMessage> messages,
    List<LlmToolSpec> tools,
    LlmToolChoice toolChoice,
    Map<String, Object> metadata,
    LlmModelSelector defaultSelector
) {

  public AgentModelSelectionContext {
    if (request == null) {
      throw new IllegalArgumentException("Agent model selection request must not be null");
    }
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent model selection step index must be positive");
    }
    if (defaultSelector == null) {
      throw new IllegalArgumentException("Agent model selection default selector must not be null");
    }
    messages = messages == null ? List.of() : List.copyOf(messages);
    tools = tools == null ? List.of() : List.copyOf(tools);
    toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public Long trustedUserId() {
    Object value = metadata.get(AgentRuntimeMetadataKeys.USER_ID);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
