package org.congcong.algomentor.llm.core.request;

import java.util.Objects;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;

/** 解析单次调用的最终 reasoning effort，优先使用请求显式值，再使用路由值。 */
public final class LlmReasoningEffortResolver {

  private LlmReasoningEffortResolver() {
  }

  public static LlmReasoningEffort resolve(LlmCompletionRequest request) {
    Objects.requireNonNull(request, "LLM request must not be null");
    return resolve(request.options(), request.invocationTarget());
  }

  public static LlmReasoningEffort resolve(
      LlmGenerationOptions options,
      LlmInvocationTarget target
  ) {
    LlmGenerationOptions effectiveOptions = options == null ? LlmGenerationOptions.defaults() : options;
    if (effectiveOptions.reasoningEffort() != null) {
      return effectiveOptions.reasoningEffort();
    }
    return target == null ? null : target.routeReasoningEffort();
  }

  public static LlmCompletionRequest apply(LlmCompletionRequest request) {
    Objects.requireNonNull(request, "LLM request must not be null");
    LlmReasoningEffort effort = resolve(request);
    if (request.options().reasoningEffort() == effort) {
      return request;
    }
    return request.withOptions(request.options().withReasoningEffort(effort));
  }
}
