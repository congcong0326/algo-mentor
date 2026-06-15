package org.congcong.algomentor.agent.core;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public class StreamingAgentRunner {

  private final Function<AgentRequest, Flow.Publisher<LlmStreamEvent>> streamRunner;

  @Deprecated(forRemoval = false)
  public StreamingAgentRunner(LlmGateway llmGateway, String model) {
    this(llmGateway, AgentLlmRequestFactory.selectorFromModel(model));
  }

  public StreamingAgentRunner(LlmGateway llmGateway, LlmModelSelector modelSelector) {
    this(request -> llmGateway.stream(AgentLlmRequestFactory.build(modelSelector, request)));
  }

  protected StreamingAgentRunner(Function<AgentRequest, Flow.Publisher<LlmStreamEvent>> streamRunner) {
    this.streamRunner = Objects.requireNonNull(streamRunner, "streamRunner must not be null");
  }

  public Flow.Publisher<LlmStreamEvent> stream(AgentRequest request) {
    return streamRunner.apply(request);
  }
}
