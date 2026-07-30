package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;

class PracticeTurnOrchestratorTest {

  @Test
  void delegatesTrustedPracticeInputToRuntimeAsUserEntry() {
    AgentStreamEvent.AgentRunEnd runEnd = new AgentStreamEvent.AgentRunEnd(
        "run-1", 1, LlmFinishReason.STOP, Map.of("existing", "value"));
    CapturingRuntime runtime = new CapturingRuntime(runEnd);
    PracticeTurnOrchestrator orchestrator = new PracticeTurnOrchestrator(runtime);

    List<AgentStreamEvent> events = collect(orchestrator.stream(input()));

    assertThat(events).containsExactly(runEnd);
    assertThat(runtime.invocation.agentKey()).isEqualTo(PracticeChatAgentDefinition.KEY);
    assertThat(runtime.invocation.input()).isEqualTo(input());
    assertThat(runtime.invocation.context().userId()).isEqualTo(7L);
    assertThat(runtime.invocation.context().mode().name()).isEqualTo("USER_ENTRY");
    assertThat(runtime.invocation.context().idempotencyKey()).isEqualTo("idem-1");
    assertThat(runtime.invocation.context().requestSize()).isEqualTo(24);
    assertThat(runtime.invocation.context().streaming()).isTrue();
  }

  private PracticeChatAgentInput input() {
    return new PracticeChatAgentInput(
        7L,
        50L,
        100L,
        12L,
        1,
        "two-sum",
        "给我一个提示",
        "idem-1",
        "zh-CN",
        PracticeCoachStyle.GUIDED,
        PracticeResponseLanguage.ZH_CN,
        24);
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.completed).isTrue();
    return subscriber.events;
  }

  private static final class CapturingRuntime implements AgentRuntime {
    private final List<AgentStreamEvent> events;
    private AgentInvocation<?> invocation;

    private CapturingRuntime(AgentStreamEvent... events) {
      this.events = List.of(events);
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("execute not used");
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      this.invocation = invocation;
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed;

        @Override
        public void request(long n) {
          if (completed || n <= 0) {
            return;
          }
          completed = true;
          events.forEach(subscriber::onNext);
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
          completed = true;
        }
      });
    }
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onComplete() {
      completed = true;
    }
  }
}
