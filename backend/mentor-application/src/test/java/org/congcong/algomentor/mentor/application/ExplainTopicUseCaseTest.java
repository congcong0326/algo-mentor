package org.congcong.algomentor.mentor.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.mentor.application.topic.TopicExplanationAgentInput;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;

class ExplainTopicUseCaseTest {

  @Test
  void delegatesSynchronousTopicExplanationToAgentRuntime() {
    RecordingRuntime runtime = new RecordingRuntime();
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(runtime);

    String explanation = useCase.explain(7L, "binary search");

    assertThat(explanation).isEqualTo("Explain binary search with invariants.");
    assertThat(runtime.lastInvocation.agentKey().value()).isEqualTo(AiBusinessScenario.TOPIC_EXPLANATION.code());
    assertThat(runtime.lastInvocation.context().mode()).isEqualTo(AgentInvocationMode.USER_ENTRY);
    assertThat(runtime.lastInvocation.context().streaming()).isFalse();
    assertThat(input(runtime.lastInvocation).topic().title()).isEqualTo("binary search");
  }

  @Test
  void delegatesStreamingTopicExplanationToTheSameRuntimeDefinition() {
    RecordingRuntime runtime = new RecordingRuntime();
    ExplainTopicUseCase useCase = new ExplainTopicUseCase(runtime);

    Flow.Publisher<AgentStreamEvent> publisher = useCase.stream("binary search", 7L);

    assertThat(publisher).isSameAs(runtime.publisher);
    assertThat(runtime.lastInvocation.agentKey().value()).isEqualTo(AiBusinessScenario.TOPIC_EXPLANATION.code());
    assertThat(runtime.lastInvocation.context().streaming()).isTrue();
    assertThat(runtime.lastInvocation.context().requestSize()).isPositive();
    assertThat(input(runtime.lastInvocation).displayMetadata()).containsEntry("topicCharCount", 13);
  }

  private static TopicExplanationAgentInput input(AgentInvocation<?> invocation) {
    return (TopicExplanationAgentInput) invocation.input();
  }

  private static final class RecordingRuntime implements AgentRuntime {

    private final SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
    private AgentInvocation<?> lastInvocation;

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      lastInvocation = invocation;
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("Explain binary search with invariants.", null, null, null, Map.of()),
          Map.of());
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      lastInvocation = invocation;
      return publisher;
    }
  }
}
