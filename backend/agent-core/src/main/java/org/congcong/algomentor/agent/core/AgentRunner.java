package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;

public class AgentRunner {

  private final Function<AgentRequest, AgentResponse> runner;

  public AgentRunner(LlmGateway llmGateway, String model) {
    this(request -> new AgentResponse(llmGateway.complete(buildRequest(model, request)).message().text()));
  }

  protected AgentRunner(Function<AgentRequest, AgentResponse> runner) {
    this.runner = Objects.requireNonNull(runner, "runner must not be null");
  }

  public AgentResponse run(AgentRequest request) {
    return runner.apply(request);
  }

  private static LlmCompletionRequest buildRequest(String model, AgentRequest request) {
    String prompt = "Explain the learning topic for an algorithm student: " + request.topic().title();
    return LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation"))
        .messages(List.of(LlmMessage.user(prompt)))
        .build();
  }
}
