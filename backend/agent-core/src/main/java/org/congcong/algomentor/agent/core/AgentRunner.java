package org.congcong.algomentor.agent.core;

import java.util.Objects;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;

public class AgentRunner {

  private final Function<AgentRequest, AgentResponse> runner;

  public AgentRunner(LlmGateway llmGateway, String model) {
    this(request -> new AgentResponse(
        llmGateway.complete(AgentLlmRequestFactory.build(model, request)).message().text()));
  }

  protected AgentRunner(Function<AgentRequest, AgentResponse> runner) {
    this.runner = Objects.requireNonNull(runner, "runner must not be null");
  }

  public AgentResponse run(AgentRequest request) {
    return runner.apply(request);
  }
}
