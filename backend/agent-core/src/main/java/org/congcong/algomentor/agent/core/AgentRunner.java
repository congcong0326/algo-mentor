package org.congcong.algomentor.agent.core;

import java.util.Objects;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;

public class AgentRunner {

  private final Function<AgentRequest, AgentResponse> runner;

  @Deprecated(forRemoval = false)
  public AgentRunner(LlmGateway llmGateway, String model) {
    this(llmGateway, AgentLlmRequestFactory.selectorFromModel(model));
  }

  public AgentRunner(LlmGateway llmGateway, LlmModelSelector modelSelector) {
    this(request -> new AgentResponse(
        llmGateway.complete(AgentLlmRequestFactory.build(modelSelector, request)).message().text()));
  }

  protected AgentRunner(Function<AgentRequest, AgentResponse> runner) {
    this.runner = Objects.requireNonNull(runner, "runner must not be null");
  }

  public AgentResponse run(AgentRequest request) {
    return runner.apply(request);
  }
}
